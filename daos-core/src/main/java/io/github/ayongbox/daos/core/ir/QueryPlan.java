package io.github.ayongbox.daos.core.ir;

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
            List<String> groupBy) implements QueryPlan {

        public Select {
            Objects.requireNonNull(physicalTable, "physicalTable");
            items = items == null ? List.of() : List.copyOf(items);
            filter = filter == null ? FilterNode.alwaysTrue() : filter;
            orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
            groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
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
            this(physicalTable, items, filter, orderBy, limit, offset, List.of());
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
                    orderBy, limit, offset, groupBy);
        }

        public Select withLimit(int newLimit, int newOffset) {
            return new Select(physicalTable, items, filter, orderBy, newLimit, newOffset, groupBy);
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
