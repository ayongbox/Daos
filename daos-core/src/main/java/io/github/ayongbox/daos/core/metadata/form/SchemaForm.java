package io.github.ayongbox.daos.core.metadata.form;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;
import io.github.ayongbox.daos.core.metadata.common.FormScene;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表单元数据。
 */
public record SchemaForm(
        String id,
        String appId,
        String tableId,
        String code,
        String displayName,
        String description,
        FormType formType,
        Map<FormScene, FormLayout> scenes,
        List<FormField> fields,
        List<ValidationRule> rules,
        List<LinkageRule> linkages,
        List<FormEvent> events,
        MetadataStatus status,
        int version,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public SchemaForm {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");
        formType = formType == null ? FormType.RECORD : formType;
        scenes = scenes == null ? Map.of() : Map.copyOf(scenes);
        fields = fields == null ? List.of() : List.copyOf(fields);
        rules = rules == null ? List.of() : List.copyOf(rules);
        linkages = linkages == null ? List.of() : List.copyOf(linkages);
        events = events == null ? List.of() : List.copyOf(events);
        status = status == null ? MetadataStatus.DRAFT : status;
        version = version == 0 ? 1 : version;
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }
}
