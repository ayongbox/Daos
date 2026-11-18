package io.github.ayongbox.daos.core.ir;

import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;

import java.util.List;
import java.util.Objects;

/**
 * 查询计划。与方言无关的 AST，权限注入在这一层完成，方言渲染器只负责翻译。
 */
public sealed interface QueryPlan {

    record Select(
            String physicalTable,
            List<SelectItem> items,
            FilterNode filter,
            List<OrderItem> orderBy,
            int limit,
            int offset,
            List<String> groupBy,
            List<HavingItem> having) implements QueryPlan {

        public Select {
            Objects.requireNonNull(physicalTable, "physicalTable");
            items = items == null ? List.of() : List.copyOf(items);
            filter = filter == null ? FilterNode.alwaysTrue() : filter;
            orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
            groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
            having = having == null ? List.of() : List.copyOf(having);
        }

        /**
         * 兼容尚未包含 HAVING 的调用方。
         */
        public Select(
                String physicalTable,
                List<SelectItem> items,
                FilterNode filter,
                List<OrderItem> orderBy,
                int limit,
                int offset,
                List<String> groupBy) {
            this(physicalTable, items, filter, orderBy, limit, offset, groupBy, List.of());
        }

        /**
         * 兼容普通（非聚合）查询的构造方式。
         */
        public Select(
                String physicalTable,
                List<SelectItem> items,
                FilterNode filter,
                List<OrderItem> orderBy,
                int limit,
                int offset) {
            this(physicalTable, items, filter, orderBy, limit, offset, List.of(), List.of());
        }

        /**
         * 是否包含聚合投影。聚合查询不再追加主键与版本列，也不能按记录身份排序。
         */
        public boolean aggregated() {
            return !groupBy.isEmpty() || items.stream().anyMatch(item -> item.function() != null);
        }

        /**
         * 权限重写入口：在既有条件上 AND 一层租户与数据范围条件。
         */
        public Select withFilter(FilterNode additional) {
            if (additional == null) {
                return this;
            }
            return new Select(physicalTable, items, FilterNode.and(filter, additional),
                    orderBy, limit, offset, groupBy, having);
        }

        public Select withLimit(int newLimit, int newOffset) {
            return new Select(physicalTable, items, filter, orderBy, newLimit, newOffset,
                    groupBy, having);
        }

        /**
         * 计数子查询使用：去掉排序与分页，保留分组与 HAVING。
         */
        public Select forCount() {
            return new Select(physicalTable, items, filter, List.of(), 0, 0, groupBy, having);
        }
    }

    /**
     * HAVING 条件。{@code alias} 指向聚合投影的别名，渲染时展开为聚合表达式，
     * 避免依赖各数据库对 HAVING 中使用别名的支持差异。
     */
    record HavingItem(String alias, ConditionOperator operator, List<Object> values) {
        public HavingItem {
            Objects.requireNonNull(alias, "alias");
            Objects.requireNonNull(operator, "operator");
            values = values == null ? List.of() : List.copyOf(values);
            switch (operator) {
                case EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN -> {
                }
                default -> throw new IllegalArgumentException(
                        "HAVING 不支持运算符: " + operator.code());
            }
            if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("HAVING 条件值不能为空: " + alias);
            }
        }
    }

    record SelectItem(String column, String alias, AggregateFunction function) {
        public SelectItem {
            Objects.requireNonNull(column, "column");
            alias = alias == null ? column : alias;
        }

        public SelectItem(String column, String alias) {
            this(column, alias, null);
        }
    }

    record OrderItem(String column, boolean ascending) {
        public OrderItem {
            Objects.requireNonNull(column, "column");
        }
    }
}
