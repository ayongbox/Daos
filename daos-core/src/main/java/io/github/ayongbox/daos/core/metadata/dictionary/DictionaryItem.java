package io.github.ayongbox.daos.core.metadata.dictionary;

import java.util.Objects;

/**
 * 字典项。停用后保留历史数据可读，但不再允许新记录选择。
 *
 * <p>{@code parentId} 只在树形字典中使用，平铺字典必须为空。</p>
 */
public record DictionaryItem(
        String id,
        String parentId,
        String code,
        String label,
        String value,
        String color,
        String icon,
        int sortOrder,
        boolean enabled,
        boolean defaultSelected,
        String description) {

    public DictionaryItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        if (code.isBlank()) {
            throw new IllegalArgumentException("字典项编码不能为空");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("字典项名称不能为空");
        }
        parentId = parentId == null || parentId.isBlank() ? null : parentId;
    }
}
