package io.github.ayongbox.daos.core.metadata.permission;

import io.github.ayongbox.daos.core.metadata.common.MaskType;

import java.util.Objects;

/**
 * 字段脱敏规则。
 */
public record MaskRule(
        String fieldId,
        MaskType type,
        String pattern,
        String replacement) {

    public MaskRule {
        Objects.requireNonNull(fieldId, "fieldId");
        Objects.requireNonNull(type, "type");
        if (type == MaskType.CUSTOM && (pattern == null || pattern.isBlank())) {
            throw new IllegalArgumentException("CUSTOM 脱敏必须提供 pattern");
        }
    }
}
