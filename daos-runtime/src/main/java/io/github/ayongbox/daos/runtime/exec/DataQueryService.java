package io.github.ayongbox.daos.runtime.exec;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.pipeline.query.QueryCompiler;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.engine.query.DefaultQueryCompiler;
import io.github.ayongbox.daos.runtime.permission.FieldMasker;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriteException;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据查询服务。把「激活快照 → 归一化 IR → 查询编译 → 权限重写 → 方言渲染 → JDBC 执行」串成一条链路。
 *
 * <p>运行时只读激活快照，草稿改动不会影响查询结果。归一化结果按应用缓存，
 * 快照切换后自动失效，缓存规模与应用数同阶。</p>
 *
 * <p>租户、软删、数据范围与行级权限由 {@link PermissionRewriter} 统一注入，
 * 查询结果再由 {@link FieldMasker} 按字段掩码脱敏，本类不手工拼接权限条件。</p>
 */
public final class DataQueryService {

    private static final String ID_COLUMN = "id";
    private static final String VERSION_COLUMN = "version";

    private final SnapshotStore snapshotStore;
    private final DataAccessFacade dataAccess;
    private final SqlDialect dialect;
    private final QueryCompiler queryCompiler;
    private final SchemaNormalizer normalizer;
    private final PermissionRewriter permissionRewriter;
    private final AuditLog auditLog;
    private final FieldMasker fieldMasker = new FieldMasker();
    private final Map<String, CachedSchema> schemaCache = new ConcurrentHashMap<>();

    public DataQueryService(SnapshotStore snapshotStore, DataAccessFacade dataAccess, SqlDialect dialect) {
        this(snapshotStore, dataAccess, dialect,
                new DefaultQueryCompiler(), new SchemaNormalizer(), new PermissionRewriter(), AuditLog.noop());
    }

    public DataQueryService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            AuditLog auditLog) {
        this(snapshotStore, dataAccess, dialect,
                new DefaultQueryCompiler(), new SchemaNormalizer(), new PermissionRewriter(), auditLog);
    }

    public DataQueryService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            QueryCompiler queryCompiler,
            SchemaNormalizer normalizer) {
        this(snapshotStore, dataAccess, dialect, queryCompiler, normalizer,
                new PermissionRewriter(), AuditLog.noop());
    }

    public DataQueryService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            QueryCompiler queryCompiler,
            SchemaNormalizer normalizer,
            PermissionRewriter permissionRewriter) {
        this(snapshotStore, dataAccess, dialect, queryCompiler, normalizer,
                permissionRewriter, AuditLog.noop());
    }

    public DataQueryService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            QueryCompiler queryCompiler,
            SchemaNormalizer normalizer,
            PermissionRewriter permissionRewriter,
            AuditLog auditLog) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.dataAccess = Objects.requireNonNull(dataAccess, "dataAccess");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.queryCompiler = Objects.requireNonNull(queryCompiler, "queryCompiler");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.permissionRewriter = Objects.requireNonNull(permissionRewriter, "permissionRewriter");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
    }

    public List<Map<String, Object>> query(
            String appId, String tableId, QueryRequest request, PermissionContext permissions) {
        Rewritten rewritten = rewrite(appId, tableId, request, permissions);
        List<Map<String, Object>> rows = queryRaw(appId, rewritten, permissions);
        decorateRelations(appId, rewritten.schema(), rewritten.table(), rows, permissions);
        return rows;
    }

    private List<Map<String, Object>> queryRaw(
            String appId, Rewritten rewritten, PermissionContext permissions) {
        SqlStatement statement = dialect.query().render(withIdentity(rewritten.select()));
        List<Map<String, Object>> rows = fieldMasker.apply(
                rewritten.table(), rewritten.mask(), dataAccess.query(statement));
        auditLog.record(permissions.tenantId(), appId, permissions.subjectId(),
                AuditAction.READ, ResourceType.TABLE, rewritten.table().tableId(),
                AuditOutcome.SUCCESS, null,
                Map.of("tableCode", rewritten.table().tableCode(), "rowCount", rows.size()));
        return rows;
    }

    /**
     * 与 query 使用完全相同的租户、软删与权限条件统计总数，供分页返回。
     */
    public long count(
            String appId, String tableId, QueryRequest request, PermissionContext permissions) {
        Rewritten rewritten = rewrite(appId, tableId, request, permissions);
        List<Map<String, Object>> rows = dataAccess.query(dialect.query().renderCount(rewritten.select()));
        if (rows.isEmpty()) {
            return 0L;
        }
        Object total = rows.get(0).values().iterator().next();
        return total instanceof Number number ? number.longValue() : 0L;
    }

    private Rewritten rewrite(
            String appId, String tableId, QueryRequest request, PermissionContext permissions) {
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(permissions, "permissions");

        SchemaSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalStateException("应用没有生效版本: " + appId));

        NormalizedSchema schema = schemaOf(snapshot);
        QueryPlan plan = queryCompiler.compile(schema, tableId, request, permissions);
        if (!(plan instanceof QueryPlan.Select select)) {
            throw new IllegalStateException("不支持的查询计划: " + plan.getClass().getName());
        }
        IrTable table = schema.requireTable(tableId);
        Instant now = Instant.now();
        try {
            QueryPlan.Select rewritten = permissionRewriter.rewrite(
                    schema, table, select, request, permissions, now);
            FieldMask mask = permissionRewriter.effectiveFieldMask(schema, table, permissions, now);
            return new Rewritten(select, rewritten, table, mask, schema);
        } catch (PermissionRewriteException error) {
            auditLog.record(permissions.tenantId(), appId, permissions.subjectId(),
                    AuditAction.READ, ResourceType.TABLE, tableId, AuditOutcome.DENIED,
                    error.getMessage(), Map.of("tableCode", table.tableCode()));
            throw error;
        }
    }

    private record Rewritten(
            QueryPlan.Select requested,
            QueryPlan.Select select,
            IrTable table,
            FieldMask mask,
            NormalizedSchema schema) {
    }

    /**
     * 查询结果始终附带记录主键与版本号，前端据此编辑与乐观锁更新。
     * 两者是系统列，不参与字段权限与投影校验。
     */
    private static QueryPlan.Select withIdentity(QueryPlan.Select select) {
        if (select.aggregated()) {
            return select;
        }
        List<QueryPlan.SelectItem> items = new ArrayList<>(select.items().size() + 2);
        items.addAll(select.items());
        items.add(new QueryPlan.SelectItem(ID_COLUMN, ID_COLUMN));
        items.add(new QueryPlan.SelectItem(VERSION_COLUMN, VERSION_COLUMN));
        return new QueryPlan.Select(
                select.physicalTable(), items, select.filter(), select.orderBy(),
                select.limit(), select.offset());
    }

    /**
     * 为关联字段补充 {@code <fieldCode>_display} 展示值。按目标表批量解析，避免 N+1。
     * 目标表不可读或无权限时保留原始 id，不影响主查询。
     */
    private void decorateRelations(
            String appId,
            NormalizedSchema schema,
            IrTable table,
            List<Map<String, Object>> rows,
            PermissionContext permissions) {

        if (rows.isEmpty()) {
            return;
        }
        for (IrColumn column : table.columns()) {
            if (column.source().logicalType() != FieldLogicalType.RELATION
                    || !column.hasPhysicalColumn()
                    || !(column.source().config() instanceof FieldConfig.RelationConfig config)) {
                continue;
            }
            String fieldCode = column.fieldCode();
            if (rows.stream().noneMatch(row -> row.get(fieldCode) != null)) {
                continue;
            }
            IrTable target;
            try {
                target = schema.requireTable(config.targetTableId());
            } catch (IllegalArgumentException error) {
                continue;
            }
            Set<String> ids = new java.util.LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                ids.addAll(relationIds(row.get(fieldCode)));
            }
            if (ids.isEmpty()) {
                continue;
            }
            Map<String, String> labels = fetchRelationLabels(
                    appId, target, displayCode(target, config), ids, permissions);
            if (labels.isEmpty()) {
                continue;
            }
            for (Map<String, Object> row : rows) {
                List<String> rowIds = relationIds(row.get(fieldCode));
                if (rowIds.isEmpty()) {
                    continue;
                }
                StringJoiner display = new StringJoiner("、");
                rowIds.forEach(id -> display.add(labels.getOrDefault(id, id)));
                row.put(fieldCode + "_display", display.toString());
            }
        }
    }

    private Map<String, String> fetchRelationLabels(
            String appId,
            IrTable target,
            String displayCode,
            Set<String> ids,
            PermissionContext permissions) {

        List<String> fields = displayCode == null ? List.of() : List.of(displayCode);
        QueryRequest request = new QueryRequest(
                fields,
                new FilterNode.Compare(ID_COLUMN, ConditionOperator.IN, new ArrayList<>(ids)),
                List.of(),
                1,
                Math.min(ids.size(), QueryRequest.MAX_PAGE_SIZE));
        try {
            Rewritten rewritten = rewrite(appId, target.tableId(), request, permissions);
            List<Map<String, Object>> targetRows = queryRaw(appId, rewritten, permissions);
            Map<String, String> labels = new java.util.LinkedHashMap<>();
            for (Map<String, Object> row : targetRows) {
                Object id = row.get(ID_COLUMN);
                if (id == null) {
                    continue;
                }
                Object label = displayCode == null ? null : row.get(displayCode);
                labels.put(String.valueOf(id),
                        label == null ? String.valueOf(id) : String.valueOf(label));
            }
            return labels;
        } catch (RuntimeException error) {
            return Map.of();
        }
    }

    private String displayCode(IrTable target, FieldConfig.RelationConfig config) {
        if (config.displayFieldId() != null) {
            IrColumn configured = target.columnByFieldId(config.displayFieldId());
            if (configured != null) {
                return configured.fieldCode();
            }
        }
        try {
            return target.nameColumn().fieldCode();
        } catch (RuntimeException error) {
            return null;
        }
    }

    /**
     * 关联值既可能是单个 id，也可能是 JSON 数组文本；统一解析为 id 列表。
     */
    private List<String> relationIds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof java.util.Collection<?> values) {
            return values.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            String body = text.substring(1, text.length() - 1).trim();
            if (body.isEmpty()) {
                return List.of();
            }
            return java.util.Arrays.stream(body.split(","))
                    .map(item -> item.trim().replaceAll("^\"|\"$", ""))
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        return List.of(text);
    }

    private NormalizedSchema schemaOf(SchemaSnapshot snapshot) {
        CachedSchema cached = schemaCache.get(snapshot.appId());
        if (cached != null && cached.snapshotId().equals(snapshot.snapshotId())) {
            return cached.schema();
        }

        NormalizedSchema schema = normalizer.normalize(
                snapshot.appCode(), snapshot.snapshotId(), snapshot.releaseVersion(), snapshot.app().tables());
        schemaCache.put(snapshot.appId(), new CachedSchema(snapshot.snapshotId(), schema));
        return schema;
    }

    private record CachedSchema(String snapshotId, NormalizedSchema schema) {
    }
}
