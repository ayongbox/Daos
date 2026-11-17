package io.github.ayongbox.daos.runtime.exec;

import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.pipeline.query.QueryCompiler;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.engine.query.DefaultQueryCompiler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据查询服务。把「激活快照 → 归一化 IR → 查询编译 → 方言渲染 → JDBC 执行」串成一条链路。
 *
 * <p>运行时只读激活快照，草稿改动不会影响查询结果。归一化结果按应用缓存，
 * 快照切换后自动失效，缓存规模与应用数同阶。</p>
 *
 * <p>当前只注入租户条件；数据范围与行级权限重写在后续阶段接入，任何未解析的语义运算符
 * 都会被查询编译器拒绝。</p>
 */
public final class DataQueryService {

    private static final String TENANT_COLUMN = "tenant_id";

    private final SnapshotStore snapshotStore;
    private final DataAccessFacade dataAccess;
    private final SqlDialect dialect;
    private final QueryCompiler queryCompiler;
    private final SchemaNormalizer normalizer;
    private final Map<String, CachedSchema> schemaCache = new ConcurrentHashMap<>();

    public DataQueryService(SnapshotStore snapshotStore, DataAccessFacade dataAccess, SqlDialect dialect) {
        this(snapshotStore, dataAccess, dialect, new DefaultQueryCompiler(), new SchemaNormalizer());
    }

    public DataQueryService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            SqlDialect dialect,
            QueryCompiler queryCompiler,
            SchemaNormalizer normalizer) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.dataAccess = Objects.requireNonNull(dataAccess, "dataAccess");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.queryCompiler = Objects.requireNonNull(queryCompiler, "queryCompiler");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
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
        SqlStatement statement = dialect.query().render(withTenantFilter(plan, permissions));
        return dataAccess.query(statement);
    }

    private QueryPlan.Select withTenantFilter(QueryPlan plan, PermissionContext permissions) {
        if (!(plan instanceof QueryPlan.Select select)) {
            throw new IllegalStateException("不支持的查询计划: " + plan.getClass().getName());
        }
        if (permissions.isCrossTenant()) {
            return select;
        }
        return select.withFilter(new FilterNode.Compare(
                TENANT_COLUMN, ConditionOperator.EQ, List.of(permissions.tenantId())));
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
