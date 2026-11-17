package io.github.ayongbox.daos.runtime.engine.dialect;

import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.pipeline.Dialect;
import io.github.ayongbox.daos.core.pipeline.SqlRenderer;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 查询计划的公共渲染实现。方言差异只有 LIKE 运算符，其余语法 MySQL 与 PostgreSQL 一致。
 *
 * <p>语义运算符（IS_SELF、IN_DEPT、CURRENT_USER 等）必须在此之前被权限重写阶段解析为
 * 具体条件，渲染器遇到即报错，禁止静默降级。</p>
 */
final class StandardSqlRenderer implements SqlRenderer {

    private static final String LIKE_ESCAPE = " ESCAPE '!'";

    private final SqlDialect dialect;
    private final String likeOperator;

    StandardSqlRenderer(SqlDialect dialect, String likeOperator) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.likeOperator = Objects.requireNonNull(likeOperator, "likeOperator");
    }

    @Override
    public Dialect dialect() {
        return dialect.type();
    }

    @Override
    public SqlStatement render(QueryPlan plan) {
        if (!(plan instanceof QueryPlan.Select select)) {
            throw new IllegalArgumentException("不支持的查询计划: " + plan.getClass().getName());
        }
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        appendItems(sql, select.items());
        sql.append(" FROM ").append(dialect.quote(select.physicalTable()));

        FilterNode filter = select.filter();
        if (!(filter instanceof FilterNode.Constant constant && constant.value())) {
            sql.append(" WHERE ").append(renderFilter(filter, parameters));
        }
        if (!select.orderBy().isEmpty()) {
            sql.append(" ORDER BY ").append(renderOrderBy(select.orderBy()));
        }
        if (select.limit() > 0) {
            sql.append(" LIMIT ").append(select.limit());
            if (select.offset() > 0) {
                sql.append(" OFFSET ").append(select.offset());
            }
        }
        return new SqlStatement(sql.toString(), parameters);
    }

    private void appendItems(StringBuilder sql, List<QueryPlan.SelectItem> items) {
        if (items.isEmpty()) {
            sql.append('*');
            return;
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (QueryPlan.SelectItem item : items) {
            String column = dialect.quote(item.column());
            joiner.add(item.column().equals(item.alias()) ? column : column + " AS " + dialect.quote(item.alias()));
        }
        sql.append(joiner);
    }

    private String renderOrderBy(List<QueryPlan.OrderItem> orderBy) {
        StringJoiner joiner = new StringJoiner(", ");
        for (QueryPlan.OrderItem item : orderBy) {
            joiner.add(dialect.quote(item.column()) + (item.ascending() ? " ASC" : " DESC"));
        }
        return joiner.toString();
    }

    private String renderFilter(FilterNode node, List<Object> parameters) {
        if (node instanceof FilterNode.Constant constant) {
            return constant.value() ? "1 = 1" : "1 = 0";
        }
        if (node instanceof FilterNode.And and) {
            return join(and.children(), " AND ", true, parameters);
        }
        if (node instanceof FilterNode.Or or) {
            return join(or.children(), " OR ", false, parameters);
        }
        if (node instanceof FilterNode.IsNull isNull) {
            return dialect.quote(isNull.column()) + (isNull.negated() ? " IS NOT NULL" : " IS NULL");
        }
        if (node instanceof FilterNode.Not not) {
            return "NOT (" + renderFilter(not.child(), parameters) + ")";
        }
        return renderCompare((FilterNode.Compare) node, parameters);
    }

    private String join(List<FilterNode> children, String separator, boolean emptyValue, List<Object> parameters) {
        if (children.isEmpty()) {
            return emptyValue ? "1 = 1" : "1 = 0";
        }
        StringJoiner joiner = new StringJoiner(separator, "(", ")");
        for (FilterNode child : children) {
            joiner.add(renderFilter(child, parameters));
        }
        return joiner.toString();
    }

    private String renderCompare(FilterNode.Compare compare, List<Object> parameters) {
        String column = dialect.quote(compare.column());
        List<Object> values = compare.values();
        return switch (compare.operator()) {
            case EQ -> isNullValue(values)
                    ? column + " IS NULL"
                    : column + " = " + bindSingle(values, parameters);
            case NE -> isNullValue(values)
                    ? column + " IS NOT NULL"
                    : column + " <> " + bindSingle(values, parameters);
            case CONTAINS -> renderLike(column, values, parameters, true, true, false);
            case NOT_CONTAINS -> renderLike(column, values, parameters, true, true, true);
            case STARTS_WITH -> renderLike(column, values, parameters, false, true, false);
            case ENDS_WITH -> renderLike(column, values, parameters, true, false, false);
            case EMPTY -> "(" + column + " IS NULL OR " + column + " = '')";
            case NOT_EMPTY -> "(" + column + " IS NOT NULL AND " + column + " <> '')";
            case GT -> column + " > " + bindSingle(values, parameters);
            case GTE -> column + " >= " + bindSingle(values, parameters);
            case LT -> column + " < " + bindSingle(values, parameters);
            case LTE -> column + " <= " + bindSingle(values, parameters);
            case BETWEEN -> renderBetween(column, values, parameters);
            case IN -> renderIn(column, values, parameters, false);
            case NOT_IN -> renderIn(column, values, parameters, true);
            default -> throw new UnsupportedOperationException(
                    "运算符 " + compare.operator().code()
                            + " 必须在权限重写阶段解析为具体条件，渲染器拒绝执行");
        };
    }

    private String renderLike(
            String column,
            List<Object> values,
            List<Object> parameters,
            boolean leading,
            boolean trailing,
            boolean negated) {

        String pattern = likePattern(values, leading, trailing);
        parameters.add(pattern);
        return column + (negated ? " NOT " + likeOperator : " " + likeOperator) + " ?" + LIKE_ESCAPE;
    }

    private String likePattern(List<Object> values, boolean leading, boolean trailing) {
        Object value = requireSingle(values);
        String escaped = String.valueOf(value)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return (leading ? "%" : "") + escaped + (trailing ? "%" : "");
    }

    private String renderBetween(String column, List<Object> values, List<Object> parameters) {
        if (values.size() != 2) {
            throw new IllegalArgumentException("BETWEEN 需要 2 个值，实际 " + values.size());
        }
        parameters.add(requireValue(values.get(0)));
        parameters.add(requireValue(values.get(1)));
        return column + " BETWEEN ? AND ?";
    }

    private String renderIn(String column, List<Object> values, List<Object> parameters, boolean negated) {
        if (values.isEmpty()) {
            return negated ? "1 = 1" : "1 = 0";
        }
        values.forEach(value -> parameters.add(requireValue(value)));
        return column + (negated ? " NOT IN (" : " IN (")
                + String.join(", ", Collections.nCopies(values.size(), "?")) + ")";
    }

    private Object requireSingle(List<Object> values) {
        if (values.size() != 1) {
            throw new IllegalArgumentException("运算符需要 1 个值，实际 " + values.size());
        }
        return requireValue(values.get(0));
    }

    private String bindSingle(List<Object> values, List<Object> parameters) {
        parameters.add(requireSingle(values));
        return "?";
    }

    private static boolean isNullValue(List<Object> values) {
        return values.size() == 1 && values.get(0) == null;
    }

    private static Object requireValue(Object value) {
        return Objects.requireNonNull(value, "过滤值与索引值不能为 null");
    }
}
