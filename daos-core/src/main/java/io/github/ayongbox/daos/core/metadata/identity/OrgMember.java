package io.github.ayongbox.daos.core.metadata.identity;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;

import java.util.Map;
import java.util.Objects;

/**
 * 组织成员。id 同时是权限主体 id，数据范围 SELF 直接使用该值。
 */
public record OrgMember(
        String id,
        String tenantId,
        String username,
        String displayName,
        String email,
        String phone,
        String deptId,
        MemberStatus status,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public OrgMember {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        if (username.isBlank()) {
            throw new IllegalArgumentException("成员账号不能为空");
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("成员姓名不能为空");
        }
        status = status == null ? MemberStatus.ACTIVE : status;
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }

    public boolean active() {
        return status == MemberStatus.ACTIVE && !deleted;
    }
}
