package io.github.ayongbox.daos.core.metadata.field;

import java.util.Objects;

/**
 * 选项定义，单选 / 多选字段内联选项与字典选项共用同一结构。
 */
public record OptionItem(
        String id,
        String code,
        String label,
        String color,
        String icon,
        int sortOrder,
        boolean enabled,
        boolean defaultSelected,
        String description) {

    public OptionItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
    }
}
