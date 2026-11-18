package io.github.ayongbox.daos.core.metadata.menu;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;

import java.util.Map;
import java.util.Objects;

/**
 * 应用菜单。菜单随应用一起发布，运行时按 {@code sortOrder} 排序并过滤不可见项。
 *
 * <p>{@code targetId} 按 {@link MenuType} 指向视图、表单或表；发布时校验目标存在。</p>
 */
public record SchemaMenu(
        String id,
        String appId,
        String code,
        String displayName,
        String icon,
        MenuType menuType,
        String targetId,
        int sortOrder,
        boolean visible,
        MetadataStatus status,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public SchemaMenu {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");
        menuType = menuType == null ? MenuType.VIEW : menuType;
        Objects.requireNonNull(targetId, "targetId");
        status = status == null ? MetadataStatus.DRAFT : status;
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }
}
