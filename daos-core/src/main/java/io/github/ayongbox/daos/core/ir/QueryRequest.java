package io.github.ayongbox.daos.core.ir;

import io.github.ayongbox.daos.core.metadata.common.SortDirection;

import java.util.List;
import java.util.Objects;

/**
 * 查询请求。只表达用户意图，租户与数据范围由权限重写阶段补充。
 */
public record QueryRequest(
        List<String> fieldCodes,
        FilterNode filter,
        List<SortSpec> sorts,
        int page,
        int pageSize) {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public record SortSpec(String fieldCode, SortDirection direction) {
        public SortSpec {
            Objects.requireNonNull(fieldCode, "fieldCode");
            direction = direction == null ? SortDirection.ASC : direction;
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
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
