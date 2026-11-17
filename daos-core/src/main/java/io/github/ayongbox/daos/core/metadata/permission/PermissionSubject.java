package io.github.ayongbox.daos.core.metadata.permission;

import io.github.ayongbox.daos.core.metadata.common.SubjectType;

import java.util.Objects;

/**
 * 权限主体。
 */
public record PermissionSubject(
        SubjectType type,
        String id,
        boolean includeDescendants) {

    public PermissionSubject {
        Objects.requireNonNull(type, "type");
        if (type != SubjectType.PUBLIC && (id == null || id.isBlank())) {
            throw new IllegalArgumentException("主体类型 " + type + " 必须指定主体 id");
        }
    }
}
