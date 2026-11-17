package io.github.ayongbox.daos.runtime.exec;

import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.pipeline.query.QueryCompiler;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.engine.query.DefaultQueryCompiler;
import io.github.ayongbox.daos.runtime.permission.FieldMasker;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final SnapshotStore snapshotStore;
    private final DataAccessFacade dataAccess;
    private final SqlDialect dialect;
    private final QueryCompiler queryCompiler;
    private final SchemaNormalizer normalizer;
    private final PermissionRewriter permissionRewriter;
    private final FieldMasker fieldMasker = new FieldMasker();
    private final Map<String, CachedSchema> schemaCache = new ConcurrentHashMap<>();

    public DataQueryService(SnapshotStore snapshotStore, DataAccessFacade dataAccess, SqlDialect dialect) {
        this(snapshotStore, dataAccess, dialect,
                new DefaultQueryCompiler(), new SchemaNormalizer(), new PermissionRewriter());
    }

    public DataQueryService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            QueryCompiler queryCompiler,
            SchemaNormalizer normalizer) {
        this(snapshotStore, dataAccess, dialect, queryCompiler, normalizer, new PermissionRewriter());
    }

    public DataQueryService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            QueryCompiler queryCompiler,
            SchemaNormalizer normalizer,
            PermissionRewriter permissionRewriter) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.dataAccess = Objects.requireNonNull(dataAccess, "dataAccess");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.queryCompiler = Objects.requireNonNull(queryCompiler, "queryCompiler");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.permissionRewriter = Objects.requireNonNull(permissionRewriter, "permissionRewriter");
    }

    public List<Map<String, Object>> query(
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
        QueryPlan.Select rewritten = permissionRewriter.rewrite(
                schema, table, select, request, permissions, now);
        FieldMask mask = permissionRewriter.effectiveFieldMask(schema, table, permissions, now);
        SqlStatement statement = dialect.query().render(rewritten);
        return fieldMasker.apply(table, mask, dataAccess.query(statement));
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
