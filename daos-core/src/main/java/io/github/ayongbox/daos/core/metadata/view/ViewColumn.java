package io.github.ayongbox.daos.core.metadata.view;

import java.util.Objects;

/**
 * 视图列配置。只保存字段引用与展示参数，字段标题始终取表字段定义。
 */
public record ViewColumn(
        String fieldId,
        boolean visible,
        Integer width,
        int sortOrder) {

    public ViewColumn {
        Objects.requireNonNull(fieldId, "fieldId");
        if (width != null && (width < 40 || width > 1000)) {
            throw new IllegalArgumentException("列宽必须在 40~1000 之间: " + width);
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("列顺序不能为负数: " + sortOrder);
        }
    }
}
