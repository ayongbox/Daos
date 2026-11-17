package io.github.ayongbox.daos.core.ir;

import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;

import java.util.List;
import java.util.Objects;

/**
 * 权限上下文。行级条件在这一层求值并转为 AST，字段掩码在渲染层求值。
 */
public record PermissionContext(
        String tenantId,
        String subjectId,
        List<String> roleIds,
        List<String> deptIds,
        List<SchemaPermission> permissions) {

    public PermissionContext {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
        deptIds = deptIds == null ? List.of() : List.copyOf(deptIds);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public boolean isCrossTenant() {
        return tenantId.isBlank();
    }
}
