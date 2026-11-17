package io.github.ayongbox.daos.core.metadata.permission;

import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.AuditInfo;
import io.github.ayongbox.daos.core.metadata.common.EffectType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 权限规则。主体 + 资源 + 动作 + 条件 + 数据范围 + 字段掩码构成一条完整裁决依据。
 */
public record SchemaPermission(
        String id,
        String appId,
        String code,
        String name,
        String description,
        PermissionSubject subject,
        ResourceRef resource,
        List<ActionType> actions,
        EffectType effect,
        RowCondition condition,
        DataScope dataScope,
        FieldMask fieldMask,
        int priority,
        boolean enabled,
        String effectiveFrom,
        String effectiveTo,
        boolean inherited,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public SchemaPermission {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(resource, "resource");
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("权限 " + code + " 至少需要一个动作");
        }
        actions = List.copyOf(actions);
        effect = effect == null ? EffectType.ALLOW : effect;
        if (priority == 0) {
            priority = 100;
        }
        fieldMask = fieldMask == null ? FieldMask.empty() : fieldMask;
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }
}
