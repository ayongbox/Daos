package io.github.ayongbox.daos.core.ir;

import io.github.ayongbox.daos.core.metadata.common.SortDirection;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 查询请求。只表达用户意图，租户与数据范围由权限重写阶段补充。
 *
 * <p>聚合查询使用 {@link #groupBy()} 与 {@link #aggregates()}：
 * 分组列按 fieldCode 引用，聚合结果用 displayName 作为结果列名，未配置时自动生成。</p>
 */
public record QueryRequest(
        List<String> fieldCodes,
        FilterNode filter,
        List<SortSpec> sorts,
        int page,
        int pageSize,
        List<String> groupBy,
        List<AggregateSpec> aggregates) {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public record SortSpec(String fieldCode, SortDirection direction) {
        public SortSpec {
            Objects.requireNonNull(fieldCode, "fieldCode");
            direction = direction == null ? SortDirection.ASC : direction;
        }
    }

    /**
     * 聚合项。{@code fieldCode} 为空只允许用于 COUNT，表示 COUNT(*)。
     */
    public record AggregateSpec(
            AggregateFunction function,
            String fieldCode,
            String displayName) {

        public AggregateSpec {
            Objects.requireNonNull(function, "function");
            if (function == AggregateFunction.COUNT) {
                fieldCode = fieldCode == null || fieldCode.isBlank() ? null : fieldCode;
            } else if (fieldCode == null || fieldCode.isBlank()) {
                throw new IllegalArgumentException(function.code() + " 必须指定字段");
            }
            displayName = displayName == null || displayName.isBlank()
                    ? function.code() + (fieldCode == null ? "" : "_" + fieldCode)
                    : displayName;
            if (displayName.length() > 32) {
                throw new IllegalArgumentException("聚合列名不能超过 32 个字符: " + displayName);
            }
        }
    }

    public QueryRequest {
        fieldCodes = fieldCodes == null ? List.of() : List.copyOf(fieldCodes);
        filter = filter == null ? FilterNode.alwaysTrue() : filter;
        sorts = sorts == null ? List.of() : List.copyOf(sorts);
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        aggregates = aggregates == null ? List.of() : List.copyOf(aggregates);

        Set<String> aliases = new HashSet<>();
        for (AggregateSpec aggregate : aggregates) {
            if (!aliases.add(aggregate.displayName())) {
                throw new IllegalArgumentException("聚合列名重复: " + aggregate.displayName());
            }
        }
        Set<String> groups = new HashSet<>();
        for (String field : groupBy) {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("分组字段不能为空");
            }
            if (!groups.add(field)) {
                throw new IllegalArgumentException("分组字段重复: " + field);
            }
            if (aliases.contains(field)) {
                throw new IllegalArgumentException("分组字段与聚合列名冲突: " + field);
            }
        }
    }

    /**
     * 兼容普通查询的构造方式。
     */
    public QueryRequest(
            List<String> fieldCodes,
            FilterNode filter,
            List<SortSpec> sorts,
            int page,
            int pageSize) {
        this(fieldCodes, filter, sorts, page, pageSize, List.of(), List.of());
    }

    public boolean aggregated() {
        return !groupBy.isEmpty() || !aggregates.isEmpty();
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
