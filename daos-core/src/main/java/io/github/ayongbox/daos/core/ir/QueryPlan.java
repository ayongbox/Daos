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
            int offset) implements QueryPlan {

        public Select {
            Objects.requireNonNull(physicalTable, "physicalTable");
            items = items == null ? List.of() : List.copyOf(items);
            filter = filter == null ? FilterNode.alwaysTrue() : filter;
            orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
        }

        /**
         * 权限重写入口：在既有条件上 AND 一层租户与数据范围条件。
         */
        public Select withFilter(FilterNode additional) {
            if (additional == null) {
                return this;
            }
            return new Select(physicalTable, items, FilterNode.and(filter, additional), orderBy, limit, offset);
        }

        public Select withLimit(int newLimit, int newOffset) {
            return new Select(physicalTable, items, filter, orderBy, newLimit, newOffset);
        }
    }

    record SelectItem(String column, String alias) {
        public SelectItem {
            Objects.requireNonNull(column, "column");
            alias = alias == null ? column : alias;
        }
    }

    record OrderItem(String column, boolean ascending) {
        public OrderItem {
            Objects.requireNonNull(column, "column");
        }
    }
}
