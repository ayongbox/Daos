package io.github.ayongbox.daos.runtime.exec;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriteException;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriter;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 数据写入服务。新增、更新、删除统一经过权限裁决与行级条件，禁止业务代码直连数据库。
 *
 * <p>更新与删除把数据范围、租户、软删条件写进 WHERE，越权或版本不一致时受影响行数为 0，
 * 由 {@link WriteConflictException} 显式失败，而不是先查后改。</p>
 */
public final class DataWriteService {

    private static final String ID_COLUMN = "id";
    private static final String TENANT_COLUMN = "tenant_id";
    private static final String CREATED_BY_COLUMN = "created_by";
    private static final String CREATED_AT_COLUMN = "created_at";
    private static final String UPDATED_BY_COLUMN = "updated_by";
    private static final String UPDATED_AT_COLUMN = "updated_at";
    private static final String VERSION_COLUMN = "version";
    private static final String DELETED_COLUMN = "deleted";

    private static final Set<String> SYSTEM_COLUMNS = Set.of(
            ID_COLUMN, TENANT_COLUMN, CREATED_BY_COLUMN, CREATED_AT_COLUMN,
            UPDATED_BY_COLUMN, UPDATED_AT_COLUMN, VERSION_COLUMN, DELETED_COLUMN);

    private final SnapshotStore snapshotStore;
    private final DataAccessFacade dataAccess;
    private final SqlDialect dialect;
    private final PermissionRewriter permissionRewriter;
    private final SchemaNormalizer normalizer;
    private final AuditLog auditLog;

    public DataWriteService(SnapshotStore snapshotStore, DataAccessFacade dataAccess, SqlDialect dialect) {
        this(snapshotStore, dataAccess, dialect,
                new PermissionRewriter(), new SchemaNormalizer(), AuditLog.noop());
    }

    public DataWriteService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            AuditLog auditLog) {
        this(snapshotStore, dataAccess, dialect,
                new PermissionRewriter(), new SchemaNormalizer(), auditLog);
    }

    public DataWriteService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            PermissionRewriter permissionRewriter,
            SchemaNormalizer normalizer) {
        this(snapshotStore, dataAccess, dialect, permissionRewriter, normalizer, AuditLog.noop());
    }

    public DataWriteService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            PermissionRewriter permissionRewriter,
            SchemaNormalizer normalizer,
            AuditLog auditLog) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.dataAccess = Objects.requireNonNull(dataAccess, "dataAccess");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.permissionRewriter = Objects.requireNonNull(permissionRewriter, "permissionRewriter");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
    }

    public String insert(
            String appId, String tableId, Map<String, Object> values, PermissionContext permissions) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(permissions, "permissions");
        Loaded loaded = load(appId, tableId);
        IrTable table = loaded.table();
        Instant now = Instant.now();

        PermissionRewriter.WritePermission permission;
        try {
            permission = permissionRewriter.resolveWrite(
                    loaded.schema(), table, permissions, ActionType.CREATE, now);
        } catch (PermissionRewriteException error) {
            auditLog.record(permissions.tenantId(), appId, permissions.subjectId(),
                    AuditAction.CREATE, ResourceType.TABLE, tableId, AuditOutcome.DENIED,
                    error.getMessage());
            throw error;
        }

        Map<String, Object> supplied = new LinkedHashMap<>(values);
        Object idValue = supplied.remove(ID_COLUMN);
        Object tenantValue = supplied.remove(TENANT_COLUMN);
        Map<String, Object> business = businessValues(table, supplied, permission.fieldMask(), false);
        validateRequired(table, business, permission.fieldMask());

        String id = idValue == null ? Ids.ulid() : requireText(idValue, ID_COLUMN);

        LinkedHashMap<String, Object> columns = new LinkedHashMap<>();
        columns.put(ID_COLUMN, id);
        if (table.source().options().tenantScoped()) {
            String tenant = permissions.isCrossTenant()
                    ? requireText(tenantValue, TENANT_COLUMN) : permissions.tenantId();
            if (tenant == null || tenant.isBlank()) {
                throw new PermissionRewriteException("写入表 " + table.tableCode() + " 需要租户上下文");
            }
            columns.put(TENANT_COLUMN, tenant);
        } else if (tenantValue != null) {
            throw new PermissionRewriteException(
                    "表 " + table.tableCode() + " 未启用租户隔离，不能写入 " + TENANT_COLUMN);
        }

        if (table.source().options().auditEnabled()) {
            columns.put(CREATED_BY_COLUMN, permissions.subjectId());
            columns.put(CREATED_AT_COLUMN, Timestamp.from(now));
            columns.put(UPDATED_BY_COLUMN, permissions.subjectId());
            columns.put(UPDATED_AT_COLUMN, Timestamp.from(now));
        }
        columns.putAll(business);
        columns.put(VERSION_COLUMN, 0L);
        if (table.source().options().softDelete()) {
            columns.put(DELETED_COLUMN, Boolean.FALSE);
        }

        StringJoiner names = new StringJoiner(", ");
        columns.keySet().forEach(column -> names.add(dialect.quote(column)));
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + dialect.quote(table.physicalName())
                + " (" + names + ") VALUES (" + placeholders + ")";
        dataAccess.update(new SqlStatement(sql, new ArrayList<>(columns.values())));
        auditLog.record(permissions.tenantId(), appId, permissions.subjectId(),
                AuditAction.CREATE, ResourceType.TABLE, id, AuditOutcome.SUCCESS, null,
                Map.of("tableId", tableId));
        return id;
    }

    public int update(
            String appId,
            String tableId,
            String recordId,
            long expectedVersion,
            Map<String, Object> values,
            PermissionContext permissions) {

        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(permissions, "permissions");
        Loaded loaded = load(appId, tableId);
        IrTable table = loaded.table();
        Instant now = Instant.now();

        PermissionRewriter.WritePermission permission;
        try {
            permission = permissionRewriter.resolveWrite(
                    loaded.schema(), table, permissions, ActionType.UPDATE, now);
        } catch (PermissionRewriteException error) {
            auditLog.record(permissions.tenantId(), appId, permissions.subjectId(),
                    AuditAction.UPDATE, ResourceType.TABLE, recordId, AuditOutcome.DENIED,
                    error.getMessage(), Map.of("tableId", tableId));
            throw error;
        }
        Map<String, Object> business = businessValues(table, values, permission.fieldMask(), true);
        if (business.isEmpty()) {
            throw new PermissionRewriteException("更新表 " + table.tableCode() + " 时没有可写字段");
        }

        LinkedHashMap<String, Object> assignments = new LinkedHashMap<>(business);
        if (table.source().options().auditEnabled()) {
            assignments.put(UPDATED_BY_COLUMN, permissions.subjectId());
            assignments.put(UPDATED_AT_COLUMN, Timestamp.from(now));
        }

        StringJoiner set = new StringJoiner(", ");
        assignments.keySet().forEach(column -> set.add(dialect.quote(column) + " = ?"));
        set.add(dialect.quote(VERSION_COLUMN) + " = " + dialect.quote(VERSION_COLUMN) + " + 1");

        FilterNode where = recordScope(recordId, expectedVersion, permission.predicate());
        SqlStatement predicate = dialect.query().renderPredicate(where);

        List<Object> parameters = new ArrayList<>(assignments.values());
        parameters.addAll(predicate.parameters());
        String sql = "UPDATE " + dialect.quote(table.physicalName())
                + " SET " + set + " WHERE " + predicate.sql();
        int affected = requireOne(dataAccess.update(new SqlStatement(sql, parameters)), recordId);
        auditLog.record(permissions.tenantId(), appId, permissions.subjectId(),
                AuditAction.UPDATE, ResourceType.TABLE, recordId, AuditOutcome.SUCCESS, null,
                Map.of("tableId", tableId));
        return affected;
    }

    public int delete(
            String appId,
            String tableId,
            String recordId,
            long expectedVersion,
            PermissionContext permissions) {

        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(permissions, "permissions");
        Loaded loaded = load(appId, tableId);
        IrTable table = loaded.table();
        Instant now = Instant.now();

        PermissionRewriter.WritePermission permission;
        try {
            permission = permissionRewriter.resolveWrite(
                    loaded.schema(), table, permissions, ActionType.DELETE, now);
        } catch (PermissionRewriteException error) {
            auditLog.record(permissions.tenantId(), appId, permissions.subjectId(),
                    AuditAction.DELETE, ResourceType.TABLE, recordId, AuditOutcome.DENIED,
                    error.getMessage(), Map.of("tableId", tableId));
            throw error;
        }
        FilterNode where = recordScope(recordId, expectedVersion, permission.predicate());
        SqlStatement predicate = dialect.query().renderPredicate(where);

        SqlStatement statement;
        if (table.source().options().softDelete()) {
            StringJoiner set = new StringJoiner(", ");
            List<Object> parameters = new ArrayList<>();
            set.add(dialect.quote(DELETED_COLUMN) + " = ?");
            parameters.add(Boolean.TRUE);
            if (table.source().options().auditEnabled()) {
                set.add(dialect.quote(UPDATED_BY_COLUMN) + " = ?");
                set.add(dialect.quote(UPDATED_AT_COLUMN) + " = ?");
                parameters.add(permissions.subjectId());
                parameters.add(Timestamp.from(now));
            }
            set.add(dialect.quote(VERSION_COLUMN) + " = " + dialect.quote(VERSION_COLUMN) + " + 1");
            parameters.addAll(predicate.parameters());
            statement = new SqlStatement(
                    "UPDATE " + dialect.quote(table.physicalName()) + " SET " + set
                            + " WHERE " + predicate.sql(),
                    parameters);
        } else {
            statement = new SqlStatement(
                    "DELETE FROM " + dialect.quote(table.physicalName()) + " WHERE " + predicate.sql(),
                    predicate.parameters());
        }
        int affected = requireOne(dataAccess.update(statement), recordId);
        auditLog.record(permissions.tenantId(), appId, permissions.subjectId(),
                AuditAction.DELETE, ResourceType.TABLE, recordId, AuditOutcome.SUCCESS, null,
                Map.of("tableId", tableId, "softDelete", table.source().options().softDelete()));
        return affected;
    }

    private FilterNode recordScope(
            String recordId, long expectedVersion, FilterNode permissionPredicate) {
        return FilterNode.and(
                new FilterNode.Compare(ID_COLUMN, ConditionOperator.EQ, List.of(recordId)),
                new FilterNode.Compare(VERSION_COLUMN, ConditionOperator.EQ, List.of(expectedVersion)),
                permissionPredicate);
    }

    private int requireOne(int affected, String recordId) {
        if (affected != 1) {
            throw new WriteConflictException(
                    "记录 " + recordId + " 不存在、超出数据范围或版本已变更");
        }
        return affected;
    }

    private Map<String, Object> businessValues(
            IrTable table, Map<String, Object> values, FieldMask mask, boolean updating) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String code = entry.getKey();
            if (SYSTEM_COLUMNS.contains(code)) {
                throw new PermissionRewriteException("系统列不可直接写入: " + code);
            }
            IrColumn column = table.columnsByCode().get(code);
            if (column == null) {
                throw new PermissionRewriteException(
                        "表 " + table.tableCode() + " 不存在字段: " + code);
            }
            if (!column.hasPhysicalColumn()) {
                throw new PermissionRewriteException("字段 " + code + " 不支持直接写入");
            }
            if (mask.hidden(column.fieldId())) {
                throw new PermissionRewriteException("字段 " + code + " 对当前主体隐藏");
            }
            if (mask.readonly(column.fieldId())) {
                throw new PermissionRewriteException("字段 " + code + " 为只读字段");
            }
            FieldConstraints constraints = column.source().constraints();
            if (updating && constraints.immutable()) {
                throw new PermissionRewriteException("字段 " + code + " 不可修改");
            }
            if (updating && entry.getValue() == null && constraints.required()) {
                throw new PermissionRewriteException("字段 " + code + " 为必填字段");
            }
            result.put(column.physicalName(), entry.getValue());
        }
        return result;
    }

    private void validateRequired(IrTable table, Map<String, Object> business, FieldMask mask) {
        for (IrColumn column : table.physicalColumns()) {
            FieldConstraints constraints = column.source().constraints();
            boolean required = constraints.required() || mask.requiredFieldIds().contains(column.fieldId());
            if (!required || business.containsKey(column.physicalName())) {
                continue;
            }
            if (constraints.defaultValue() != null
                    || (constraints.defaultValueExpression() != null
                    && !constraints.defaultValueExpression().isBlank())) {
                continue;
            }
            throw new PermissionRewriteException(
                    "字段 " + column.fieldCode() + " 为必填字段，不能为空");
        }
    }

    private String requireText(Object value, String column) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new PermissionRewriteException(column + " 必须是非空字符串");
    }

    private Loaded load(String appId, String tableId) {
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(tableId, "tableId");
        SchemaSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalStateException("应用没有生效版本: " + appId));
        NormalizedSchema schema = normalizer.normalize(
                snapshot.appCode(), snapshot.snapshotId(), snapshot.releaseVersion(), snapshot.app().tables());
        return new Loaded(schema, schema.requireTable(tableId));
    }

    private record Loaded(NormalizedSchema schema, IrTable table) {
    }
}
