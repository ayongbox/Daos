package io.github.ayongbox.daos.core.metadata.field;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;

import java.util.Map;
import java.util.Objects;

/**
 * 字段元数据。字段集合必须覆盖逻辑类型的全部可配置能力，配置缺失时按类型默认值补齐。
 */
public record SchemaField(
        String id,
        String appId,
        String tableId,
        String code,
        String displayName,
        String description,
        FieldLogicalType logicalType,
        FieldConfig config,
        FieldConstraints constraints,
        FieldUiHint ui,
        MetadataStatus status,
        boolean system,
        boolean nameField,
        int sortOrder,
        String groupCode,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public SchemaField {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(logicalType, "logicalType");
        Objects.requireNonNull(config, "config");
        if (config.logicalType() != logicalType) {
            throw new IllegalArgumentException(
                    "字段 " + code + " 的 config 类型 " + config.logicalType() + " 与 logicalType " + logicalType + " 不一致");
        }
        constraints = constraints == null ? FieldConstraints.defaults() : constraints;
        ui = ui == null ? FieldUiHint.empty() : ui;
        status = status == null ? MetadataStatus.DRAFT : status;
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }
}
