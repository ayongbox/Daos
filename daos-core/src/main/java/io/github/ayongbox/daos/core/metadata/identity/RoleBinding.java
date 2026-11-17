package io.github.ayongbox.daos.core.metadata.identity;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;

import java.util.Map;
import java.util.Objects;

/**
 * 成员与角色的绑定关系。同一成员可同时持有平台角色与应用角色。
 */
public record RoleBinding(
        String id,
        String tenantId,
        String roleId,
        String memberId,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public RoleBinding {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(memberId, "memberId");
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }
}
