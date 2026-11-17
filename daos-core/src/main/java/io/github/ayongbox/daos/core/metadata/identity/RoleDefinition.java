package io.github.ayongbox.daos.core.metadata.identity;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;

import java.util.Map;
import java.util.Objects;

/**
 * 角色定义。平台角色 {@code appId} 为空，应用角色必须绑定所属应用。
 */
public record RoleDefinition(
        String id,
        String tenantId,
        String appId,
        String code,
        String name,
        RoleScope scope,
        String description,
        boolean enabled,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public RoleDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
        if (code.isBlank()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }

        boolean hasApp = appId != null && !appId.isBlank();
        if (scope == RoleScope.APPLICATION && !hasApp) {
            throw new IllegalArgumentException("应用角色必须绑定 appId: " + code);
        }
        if (scope == RoleScope.PLATFORM && hasApp) {
            throw new IllegalArgumentException("平台角色不能绑定 appId: " + code);
        }
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }
}
