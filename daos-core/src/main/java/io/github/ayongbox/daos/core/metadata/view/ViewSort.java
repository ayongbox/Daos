package io.github.ayongbox.daos.core.metadata.view;

import io.github.ayongbox.daos.core.metadata.common.SortDirection;

/**
 * 视图默认排序。数组顺序即优先级。
 *
 * <p>普通列表按 {@code fieldId} 排序；汇总视图可以按分组字段或汇总列名
 * {@code aggregation} 排序，二者只能设置一个。</p>
 */
public record ViewSort(
        String fieldId,
        String aggregation,
        SortDirection direction) {

    public ViewSort {
        if ((fieldId == null) == (aggregation == null)) {
            throw new IllegalArgumentException("排序必须且只能指定字段或汇总列");
        }
        direction = direction == null ? SortDirection.ASC : direction;
    }

    public ViewSort(String fieldId, SortDirection direction) {
        this(fieldId, null, direction);
    }
}
