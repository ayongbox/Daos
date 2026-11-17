package io.github.ayongbox.daos.core.metadata.identity;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;

import java.util.Map;
import java.util.Objects;

/**
 * 组织部门。使用物化路径 {@code /根/子/} 表达层级，便于按前缀展开子树。
 *
 * <p>部门属于租户级数据，不随应用发布。</p>
 */
public record OrgDepartment(
        String id,
        String tenantId,
        String parentId,
        String code,
        String name,
        String path,
        int sort,
        boolean enabled,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public OrgDepartment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (code.isBlank()) {
            throw new IllegalArgumentException("部门编码不能为空");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("部门名称不能为空");
        }
        if (id.equals(parentId)) {
            throw new IllegalArgumentException("部门不能以自身为父节点: " + id);
        }

        path = path == null || path.isBlank() ? "/" + id + "/" : path;
        if (!path.startsWith("/") || !path.endsWith("/") || !path.contains("/" + id + "/")) {
            throw new IllegalArgumentException("部门路径必须形如 /根/子/ 且包含自身 id: " + path);
        }
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }
}
