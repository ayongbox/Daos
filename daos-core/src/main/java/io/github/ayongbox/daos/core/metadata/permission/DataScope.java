package io.github.ayongbox.daos.core.metadata.permission;

import io.github.ayongbox.daos.core.metadata.common.DataScopeType;

import java.util.List;
import java.util.Objects;

/**
 * 数据范围。编译为行级条件，与行级权限取交集。
 */
public record DataScope(
        DataScopeType type,
        String ownerFieldId,
        String deptFieldId,
        List<String> userIds,
        List<String> deptIds,
        List<String> roleIds,
        boolean includeSubDept,
        boolean withShared) {

    public DataScope {
        Objects.requireNonNull(type, "type");
        userIds = userIds == null ? List.of() : List.copyOf(userIds);
        deptIds = deptIds == null ? List.of() : List.copyOf(deptIds);
        roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
        if (type == DataScopeType.SELF && (ownerFieldId == null || ownerFieldId.isBlank())) {
            throw new IllegalArgumentException("SELF 数据范围必须指定 ownerFieldId");
        }
        if ((type == DataScopeType.DEPT || type == DataScopeType.DEPT_TREE)
                && (deptFieldId == null || deptFieldId.isBlank())) {
            throw new IllegalArgumentException(type + " 数据范围必须指定 deptFieldId");
        }
        if (type == DataScopeType.CUSTOM && userIds.isEmpty() && deptIds.isEmpty() && roleIds.isEmpty()) {
            throw new IllegalArgumentException("CUSTOM 数据范围至少指定成员、部门或角色之一");
        }
    }

    public static DataScope all() {
        return new DataScope(DataScopeType.ALL, null, null, List.of(), List.of(), List.of(), true, false);
    }
}
