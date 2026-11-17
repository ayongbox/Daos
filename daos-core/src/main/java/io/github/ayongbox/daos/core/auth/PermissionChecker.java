package io.github.ayongbox.daos.core.auth;

import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;

/**
 * 权限裁决契约。实现位于 runtime/identity，core 只定义接口。
 */
public interface PermissionChecker {

    boolean isAllowed(String subjectId, ResourceRef resource, ActionType action);

    /**
     * 返回当前主体在某资源上的字段掩码，未配置时返回空掩码而不是 null。
     */
    io.github.ayongbox.daos.core.metadata.permission.FieldMask resolveFieldMask(String subjectId, ResourceRef resource);
}
