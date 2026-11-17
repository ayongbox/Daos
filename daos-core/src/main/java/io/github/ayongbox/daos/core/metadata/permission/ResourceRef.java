package io.github.ayongbox.daos.core.metadata.permission;

import io.github.ayongbox.daos.core.metadata.common.ResourceType;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 资源引用。编码格式：{type}:{appCode}:{objectCode}。
 */
public record ResourceRef(
        ResourceType type,
        String appCode,
        String objectCode,
        List<String> fieldCodes) {

    public ResourceRef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(appCode, "appCode");
        fieldCodes = fieldCodes == null ? List.of() : List.copyOf(fieldCodes);
        if (type == ResourceType.FIELD && fieldCodes.isEmpty()) {
            throw new IllegalArgumentException("FIELD 资源必须指定 fieldCodes");
        }
    }

    public String encode() {
        return type.name().toLowerCase(Locale.ROOT) + ":" + appCode + ":" + objectCode;
    }
}
