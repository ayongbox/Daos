package io.github.ayongbox.daos.runtime.engine.query;

import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.pipeline.query.QueryCompiler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 查询编译器默认实现。把逻辑字段编码解析为物理列，产出与方言无关的 {@link QueryPlan.Select}。
 *
 * <p>只做用户意图的编译：租户、数据范围与行级权限由独立重写阶段在 {@code filter} 上追加，
 * 本类不读取 {@code permissions}，避免权限逻辑散落在编译过程中。</p>
 *
 * <p>派生字段、JSON 成员字段、不可筛选/排序字段一律显式拒绝，禁止静默降级。</p>
 */
public final class DefaultQueryCompiler implements QueryCompiler {

    private static final Set<ConditionOperator> LOGICAL_OPERATORS = EnumSet.of(
            ConditionOperator.EQ,
            ConditionOperator.NE,
            ConditionOperator.CONTAINS,
            ConditionOperator.NOT_CONTAINS,
            ConditionOperator.STARTS_WITH,
            ConditionOperator.ENDS_WITH,
            ConditionOperator.EMPTY,
            ConditionOperator.NOT_EMPTY,
            ConditionOperator.GT,
            ConditionOperator.GTE,
            ConditionOperator.LT,
            ConditionOperator.LTE,
            ConditionOperator.BETWEEN,
            ConditionOperator.IN,
            ConditionOperator.NOT_IN);

    @Override
    public QueryPlan compile(
            NormalizedSchema schema,
            String tableId,
            QueryRequest request,
            PermissionContext permissions) {

        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(request, "request");

        IrTable table = schema.requireTable(tableId);

        return new QueryPlan.Select(
                table.physicalName(),
                projection(table, request.fieldCodes()),
                rewriteFilter(table, request.filter()),
                orderBy(table, request.sorts()),
                request.pageSize(),
                request.offset());
    }

    private List<QueryPlan.SelectItem> projection(IrTable table, List<String> fieldCodes) {
        List<QueryPlan.SelectItem> items = new ArrayList<>(fieldCodes.size());
        for (String fieldCode : fieldCodes) {
            IrColumn column = requireColumn(table, fieldCode);
            if (column.storage() == IrColumn.Storage.DERIVED) {
                throw new QueryCompileException(
                        "派生字段不能直接投影: " + table.tableCode() + "." + fieldCode);
            }
            if (column.storage() == IrColumn.Storage.JSON_MEMBER) {
                throw new QueryCompileException(
                        "JSON 成员字段暂不支持投影: " + table.tableCode() + "." + fieldCode);
            }
            items.add(new QueryPlan.SelectItem(column.physicalName(), fieldCode));
        }
        return items;
    }

    private List<QueryPlan.OrderItem> orderBy(IrTable table, List<QueryRequest.SortSpec> sorts) {
        List<QueryPlan.OrderItem> items = new ArrayList<>(sorts.size());
        for (QueryRequest.SortSpec sort : sorts) {
            IrColumn column = requireColumn(table, sort.fieldCode());
            if (!column.sortable()) {
                throw new QueryCompileException(
                        "字段不可排序: " + table.tableCode() + "." + sort.fieldCode());
            }
            items.add(new QueryPlan.OrderItem(
                    column.physicalName(), sort.direction() == SortDirection.ASC));
        }
        return items;
    }

    private FilterNode rewriteFilter(IrTable table, FilterNode node) {
        if (node instanceof FilterNode.Constant) {
            return node;
        }
        if (node instanceof FilterNode.And and) {
            return new FilterNode.And(rewriteChildren(table, and.children()));
        }
        if (node instanceof FilterNode.Or or) {
            return new FilterNode.Or(rewriteChildren(table, or.children()));
        }
        if (node instanceof FilterNode.IsNull isNull) {
            return new FilterNode.IsNull(
                    requireFilterable(table, isNull.column()).physicalName(), isNull.negated());
        }
        if (node instanceof FilterNode.Not not) {
            return new FilterNode.Not(rewriteFilter(table, not.child()));
        }

        FilterNode.Compare compare = (FilterNode.Compare) node;
        if (!LOGICAL_OPERATORS.contains(compare.operator())) {
            throw new QueryCompileException(
                    "运算符 " + compare.operator().code() + " 必须在权限重写阶段解析，编译阶段拒绝");
        }
        return new FilterNode.Compare(
                requireFilterable(table, compare.column()).physicalName(),
                compare.operator(),
                compare.values());
    }

    private List<FilterNode> rewriteChildren(IrTable table, List<FilterNode> children) {
        List<FilterNode> rewritten = new ArrayList<>(children.size());
        for (FilterNode child : children) {
            rewritten.add(rewriteFilter(table, child));
        }
        return rewritten;
    }

    private IrColumn requireColumn(IrTable table, String fieldCode) {
        IrColumn column = table.columnsByCode().get(fieldCode);
        if (column == null) {
            throw new QueryCompileException("表 " + table.tableCode() + " 不存在字段: " + fieldCode);
        }
        return column;
    }

    private IrColumn requireFilterable(IrTable table, String fieldCode) {
        IrColumn column = requireColumn(table, fieldCode);
        if (!column.filterable()) {
            throw new QueryCompileException(
                    "字段不可筛选: " + table.tableCode() + "." + fieldCode);
        }
        return column;
    }
}
