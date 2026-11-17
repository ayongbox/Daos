package io.github.ayongbox.daos.core.metadata.resource;

import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;

import java.util.Locale;
import java.util.Objects;

/**
 * 权限点。资源类型与动作的合法组合，是鉴权、配置界面与接口注解共用的稳定编码。
 *
 * <p>编码格式：{@code resourceType:action}，例如 {@code table:read}。编码一经发布不得更改。</p>
 */
public record PermissionPoint(
        ResourceType resourceType,
        ActionType action) {

    public PermissionPoint {
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(action, "action");
    }

    public String code() {
        return resourceType.name().toLowerCase(Locale.ROOT) + ":" + action.code();
    }
}
