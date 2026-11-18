package io.github.ayongbox.daos.runtime.view;

import io.github.ayongbox.daos.core.ir.AggregateFunction;
import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.view.SchemaView;
import io.github.ayongbox.daos.core.metadata.view.ViewColumn;
import io.github.ayongbox.daos.core.metadata.view.ViewFilter;
import io.github.ayongbox.daos.core.metadata.view.ViewSort;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 视图运行时。只读生效快照，把视图配置解析为字段与查询条件后复用数据查询服务，
 * 因此租户隔离、行级权限与字段掩码与记录接口完全一致。
 */
public final class ViewService {

    private final SnapshotStore snapshotStore;
    private final DataQueryService queryService;

    public ViewService(SnapshotStore snapshotStore, DataQueryService queryService) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
    }

    public List<ViewSummary> listViews(String appId) {
        SchemaSnapshot snapshot = requireActive(appId);
        return snapshot.app().views().stream()
                .filter(view -> !view.deleted())
                .map(view -> new ViewSummary(
                        view.id(),
                        view.code(),
                        view.displayName(),
                        view.tableId(),
                        table(snapshot, view.tableId()).code(),
                        view.viewType().name()))
                .toList();
    }

    public ViewDetail render(String appId, String viewCode) {
        SchemaSnapshot snapshot = requireActive(appId);
        SchemaView view = requireView(snapshot, viewCode);
        SchemaTable table = table(snapshot, view.tableId());
        Map<String, SchemaField> fieldsById = fieldsById(table);

        List<ViewColumnSpec> columns = resolvedColumns(view, table, fieldsById);
        List<ViewFilterSpec> filters = view.filters().stream()
                .map(filter -> new ViewFilterSpec(
                        filter.fieldId(),
                        requireField(fieldsById, filter.fieldId(), view.code()).code(),
                        filter.operator(),
                        filter.value()))
                .toList();
        List<ViewSortSpec> sorts = view.sorts().stream()
                .map(sort -> new ViewSortSpec(
                        sort.fieldId(),
                        requireField(fieldsById, sort.fieldId(), view.code()).code(),
                        sort.direction().name()))
                .toList();
        List<ViewGroupSpec> groupBy = view.groupBy().stream()
                .map(fieldId -> {
                    SchemaField field = requireField(fieldsById, fieldId, view.code());
                    return new ViewGroupSpec(field.id(), field.code(), field.displayName());
                })
                .toList();
        List<ViewAggregationSpec> aggregations = view.aggregations().stream()
                .map(aggregate -> {
                    SchemaField field = aggregate.fieldId() == null
                            ? null
                            : requireField(fieldsById, aggregate.fieldId(), view.code());
                    return new ViewAggregationSpec(
                            aggregate.function(),
                            aggregate.fieldId(),
                            field == null ? null : field.code(),
                            aggregate.displayName());
                })
                .toList();

        return new ViewDetail(
                view.id(),
                view.code(),
                view.displayName(),
                view.tableId(),
                table.code(),
                view.viewType().name(),
                view.pageSize(),
                columns,
                filters,
                sorts,
                groupBy,
                aggregations);
    }

    /**
     * 执行视图的汇总查询。未配置汇总列时拒绝，避免退化成普通列表查询。
     */
    public ViewAggregatePage aggregate(
            String appId, String viewCode, long current, long size, PermissionContext permissions) {
        SchemaSnapshot snapshot = requireActive(appId);
        SchemaView view = requireView(snapshot, viewCode);
        if (view.aggregations().isEmpty()) {
            throw new IllegalArgumentException("视图未配置汇总列: " + viewCode);
        }
        SchemaTable table = table(snapshot, view.tableId());
        Map<String, SchemaField> fieldsById = fieldsById(table);

        List<String> groupCodes = view.groupBy().stream()
                .map(fieldId -> requireField(fieldsById, fieldId, view.code()).code())
                .toList();
        List<QueryRequest.AggregateSpec> aggregates = view.aggregations().stream()
                .map(aggregate -> new QueryRequest.AggregateSpec(
                        aggregate.function(),
                        aggregate.fieldId() == null
                                ? null
                                : requireField(fieldsById, aggregate.fieldId(), view.code()).code(),
                        aggregate.displayName()))
                .toList();

        int pageSize = size > 0
                ? (int) Math.min(size, QueryRequest.MAX_PAGE_SIZE)
                : view.pageSize();
        QueryRequest request = new QueryRequest(
                List.of(),
                filterOf(view, fieldsById),
                aggregateSorts(view, fieldsById),
                (int) Math.max(1, current),
                pageSize,
                groupCodes,
                aggregates);

        long total = queryService.count(appId, table.id(), request, permissions);
        List<Map<String, Object>> rows = queryService.query(appId, table.id(), request, permissions);
        return new ViewAggregatePage(total, request.page(), request.pageSize(), rows);
    }

    private List<QueryRequest.SortSpec> aggregateSorts(
            SchemaView view, Map<String, SchemaField> fieldsById) {
        Set<String> groupFieldIds = Set.copyOf(view.groupBy());
        return view.sorts().stream()
                .map(sort -> {
                    if (!groupFieldIds.contains(sort.fieldId())) {
                        throw new IllegalArgumentException(
                                "汇总视图只能按分组字段排序: " + view.code());
                    }
                    return new QueryRequest.SortSpec(
                            requireField(fieldsById, sort.fieldId(), view.code()).code(),
                            sort.direction());
                })
                .toList();
    }

    public ViewPage query(String appId, String viewCode, long current, long size, PermissionContext permissions) {
        SchemaSnapshot snapshot = requireActive(appId);
        SchemaView view = requireView(snapshot, viewCode);
        SchemaTable table = table(snapshot, view.tableId());
        Map<String, SchemaField> fieldsById = fieldsById(table);

        List<String> fieldCodes = resolvedColumns(view, table, fieldsById).stream()
                .filter(ViewColumnSpec::visible)
                .map(ViewColumnSpec::fieldCode)
                .toList();
        if (fieldCodes.isEmpty()) {
            fieldCodes = table.fields().stream()
                    .filter(field -> !field.deleted())
                    .filter(field -> !field.ui().hiddenInList())
                    .filter(field -> !field.logicalType().computed())
                    .map(SchemaField::code)
                    .toList();
        }

        int pageSize = size > 0
                ? (int) Math.min(size, QueryRequest.MAX_PAGE_SIZE)
                : view.pageSize();
        QueryRequest request = new QueryRequest(
                fieldCodes,
                filterOf(view, fieldsById),
                sortsOf(view, fieldsById),
                (int) Math.max(1, current),
                pageSize);

        long total = queryService.count(appId, table.id(), request, permissions);
        List<Map<String, Object>> records = queryService.query(appId, table.id(), request, permissions);
        return new ViewPage(total, request.page(), request.pageSize(), records);
    }

    private FilterNode filterOf(SchemaView view, Map<String, SchemaField> fieldsById) {
        List<FilterNode> children = new ArrayList<>();
        for (ViewFilter filter : view.filters()) {
            SchemaField field = requireField(fieldsById, filter.fieldId(), view.code());
            children.add(toFilterNode(field.code(), filter));
        }
        if (children.isEmpty()) {
            return FilterNode.alwaysTrue();
        }
        return new FilterNode.And(children);
    }

    private FilterNode toFilterNode(String fieldCode, ViewFilter filter) {
        if (filter.operator() == ConditionOperator.EMPTY
                || filter.operator() == ConditionOperator.NOT_EMPTY) {
            return new FilterNode.IsNull(
                    fieldCode, filter.operator() == ConditionOperator.NOT_EMPTY);
        }
        List<Object> values = filter.value() instanceof List<?> list
                ? new ArrayList<>(list)
                : filter.value() == null ? List.of() : List.of(filter.value());
        return new FilterNode.Compare(fieldCode, filter.operator(), values);
    }

    private List<QueryRequest.SortSpec> sortsOf(SchemaView view, Map<String, SchemaField> fieldsById) {
        return view.sorts().stream()
                .map(sort -> new QueryRequest.SortSpec(
                        requireField(fieldsById, sort.fieldId(), view.code()).code(),
                        sort.direction()))
                .toList();
    }

    private List<ViewColumnSpec> resolvedColumns(
            SchemaView view, SchemaTable table, Map<String, SchemaField> fieldsById) {
        if (view.columns().isEmpty()) {
            return table.fields().stream()
                    .filter(field -> !field.deleted())
                    .filter(field -> !field.ui().hiddenInList())
                    .filter(field -> !field.logicalType().computed())
                    .sorted(Comparator.comparingInt(SchemaField::sortOrder))
                    .map(field -> new ViewColumnSpec(
                            field.id(), field.code(), field.displayName(), true, null, field.sortOrder()))
                    .toList();
        }

        return view.columns().stream()
                .sorted(Comparator.comparingInt(ViewColumn::sortOrder))
                .map(column -> {
                    SchemaField field = requireField(fieldsById, column.fieldId(), view.code());
                    return new ViewColumnSpec(
                            field.id(),
                            field.code(),
                            field.displayName(),
                            column.visible(),
                            column.width(),
                            column.sortOrder());
                })
                .toList();
    }

    private static SchemaView requireView(SchemaSnapshot snapshot, String viewCode) {
        return snapshot.app().views().stream()
                .filter(view -> !view.deleted())
                .filter(view -> view.code().equals(viewCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("视图不存在: " + viewCode));
    }

    private static SchemaTable table(SchemaSnapshot snapshot, String tableId) {
        return snapshot.app().tables().stream()
                .filter(table -> !table.deleted())
                .filter(table -> table.id().equals(tableId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("视图指向的表不存在: " + tableId));
    }

    private static Map<String, SchemaField> fieldsById(SchemaTable table) {
        Map<String, SchemaField> result = new LinkedHashMap<>();
        table.fields().stream()
                .filter(field -> !field.deleted())
                .forEach(field -> result.put(field.id(), field));
        return result;
    }

    private static SchemaField requireField(
            Map<String, SchemaField> fieldsById, String fieldId, String viewCode) {
        SchemaField field = fieldsById.get(fieldId);
        if (field == null) {
            throw new IllegalArgumentException("视图 " + viewCode + " 引用了不存在的字段: " + fieldId);
        }
        return field;
    }

    private SchemaSnapshot requireActive(String appId) {
        Objects.requireNonNull(appId, "appId");
        return snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalStateException("应用还没有生效版本: " + appId));
    }

    public record ViewSummary(
            String viewId,
            String viewCode,
            String displayName,
            String tableId,
            String tableCode,
            String viewType) {
    }

    public record ViewDetail(
            String viewId,
            String viewCode,
            String displayName,
            String tableId,
            String tableCode,
            String viewType,
            int pageSize,
            List<ViewColumnSpec> columns,
            List<ViewFilterSpec> filters,
            List<ViewSortSpec> sorts,
            List<ViewGroupSpec> groupBy,
            List<ViewAggregationSpec> aggregations) {
    }

    public record ViewGroupSpec(
            String fieldId,
            String fieldCode,
            String label) {
    }

    public record ViewAggregationSpec(
            AggregateFunction function,
            String fieldId,
            String fieldCode,
            String displayName) {
    }

    public record ViewAggregatePage(
            long total,
            int current,
            int size,
            List<Map<String, Object>> rows) {
    }

    public record ViewColumnSpec(
            String fieldId,
            String fieldCode,
            String label,
            boolean visible,
            Integer width,
            int sortOrder) {
    }

    public record ViewFilterSpec(
            String fieldId,
            String fieldCode,
            ConditionOperator operator,
            Object value) {
    }

    public record ViewSortSpec(
            String fieldId,
            String fieldCode,
            String direction) {
    }

    public record ViewPage(
            long total,
            int current,
            int size,
            List<Map<String, Object>> records) {
    }
}
