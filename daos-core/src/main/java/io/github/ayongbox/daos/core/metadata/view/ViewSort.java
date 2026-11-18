package io.github.ayongbox.daos.core.metadata.view;

import io.github.ayongbox.daos.core.metadata.common.SortDirection;

import java.util.Objects;

/**
 * 视图默认排序。数组顺序即优先级。
 */
public record ViewSort(
        String fieldId,
        SortDirection direction) {

    public ViewSort {
        Objects.requireNonNull(fieldId, "fieldId");
        direction = direction == null ? SortDirection.ASC : direction;
    }
}
