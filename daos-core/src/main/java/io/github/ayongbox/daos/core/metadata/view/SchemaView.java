package io.github.ayongbox.daos.core.metadata.view;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 列表视图元数据。列、筛选与排序只引用表字段，不复制字段定义。
 *
 * <p>视图随应用发布进入快照；运行时只读取生效快照，草稿改动需发布后才可见。</p>
 */
public record SchemaView(
        String id,
        String appId,
        String tableId,
        String code,
        String displayName,
        String description,
        ViewType viewType,
        List<ViewColumn> columns,
        List<ViewFilter> filters,
        List<ViewSort> sorts,
        List<String> groupBy,
        List<ViewAggregation> aggregations,
        List<ViewHaving> having,
        int pageSize,
        MetadataStatus status,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public SchemaView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");

        viewType = viewType == null ? ViewType.GRID : viewType;
        columns = columns == null ? List.of() : List.copyOf(columns);
        filters = filters == null ? List.of() : List.copyOf(filters);
        sorts = sorts == null ? List.of() : List.copyOf(sorts);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        aggregations = aggregations == null ? List.of() : List.copyOf(aggregations);
        having = having == null ? List.of() : List.copyOf(having);
        pageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        status = status == null ? MetadataStatus.DRAFT : status;
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }

    /**
     * 兼容尚未包含 HAVING 的调用方。
     */
    public SchemaView(
            String id,
            String appId,
            String tableId,
            String code,
            String displayName,
            String description,
            ViewType viewType,
            List<ViewColumn> columns,
            List<ViewFilter> filters,
            List<ViewSort> sorts,
            List<String> groupBy,
            List<ViewAggregation> aggregations,
            int pageSize,
            MetadataStatus status,
            Map<String, Object> ext,
            String createdBy,
            String createdAt,
            String updatedBy,
            String updatedAt,
            boolean deleted,
            int schemaVersion) {
        this(id, appId, tableId, code, displayName, description, viewType,
                columns, filters, sorts, groupBy, aggregations, List.of(), pageSize, status, ext,
                createdBy, createdAt, updatedBy, updatedAt, deleted, schemaVersion);
    }

    /**
     * 兼容尚未包含分组与汇总的调用方。
     */
    public SchemaView(
            String id,
            String appId,
            String tableId,
            String code,
            String displayName,
            String description,
            ViewType viewType,
            List<ViewColumn> columns,
            List<ViewFilter> filters,
            List<ViewSort> sorts,
            int pageSize,
            MetadataStatus status,
            Map<String, Object> ext,
            String createdBy,
            String createdAt,
            String updatedBy,
            String updatedAt,
            boolean deleted,
            int schemaVersion) {
        this(id, appId, tableId, code, displayName, description, viewType,
                columns, filters, sorts, List.of(), List.of(), List.of(), pageSize, status, ext,
                createdBy, createdAt, updatedBy, updatedAt, deleted, schemaVersion);
    }
}
