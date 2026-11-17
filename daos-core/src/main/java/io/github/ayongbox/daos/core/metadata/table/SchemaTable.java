package io.github.ayongbox.daos.core.metadata.table;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 表元数据。字段、索引、关联均内聚在表上，发布时整体编译。
 *
 * <p>表不是一等公民：应用才是。{@code ownership} 区分平台表与应用表，
 * 跨应用共享只能通过 {@link ExternalTableRef} 做只读引用，不允许共同拥有。</p>
 */
public record SchemaTable(
        String id,
        String appId,
        TableOwnership ownership,
        boolean shareable,
        StorageMode storageMode,
        List<String> promotedColumns,
        ExternalTableRef externalRef,
        String code,
        String displayName,
        String description,
        String icon,
        String color,
        String groupCode,
        int sortOrder,
        TableKind kind,
        MetadataStatus status,
        boolean system,
        String nameFieldId,
        List<String> subtitleFieldIds,
        String defaultSortFieldId,
        SortDirection defaultSortDirection,
        TableOptions options,
        List<SchemaField> fields,
        List<IndexDef> indexes,
        List<RelationDef> relations,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public static final int MAX_FIELDS = 200;
    public static final int MAX_INDEXES = 20;
    public static final int MAX_RELATIONS = 30;

    public SchemaTable {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");
        ownership = ownership == null ? TableOwnership.APPLICATION : ownership;
        storageMode = storageMode == null ? StorageMode.PHYSICAL : storageMode;
        promotedColumns = promotedColumns == null ? List.of() : List.copyOf(promotedColumns);
        kind = kind == null ? TableKind.MAIN : kind;
        status = status == null ? MetadataStatus.DRAFT : status;
        defaultSortDirection = defaultSortDirection == null ? SortDirection.DESC : defaultSortDirection;
        options = options == null ? TableOptions.defaults() : options;
        fields = fields == null ? List.of() : List.copyOf(fields);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
        relations = relations == null ? List.of() : List.copyOf(relations);
        subtitleFieldIds = subtitleFieldIds == null ? List.of() : List.copyOf(subtitleFieldIds);
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;

        if (fields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("表 " + code + " 字段数超过上限 " + MAX_FIELDS);
        }
        if (indexes.size() > MAX_INDEXES) {
            throw new IllegalArgumentException("表 " + code + " 索引数超过上限 " + MAX_INDEXES);
        }
        if (relations.size() > MAX_RELATIONS) {
            throw new IllegalArgumentException("表 " + code + " 关联数超过上限 " + MAX_RELATIONS);
        }
        if (subtitleFieldIds.size() > 3) {
            throw new IllegalArgumentException("表 " + code + " 副标题字段最多 3 个");
        }
        if (ownership == TableOwnership.PLATFORM && externalRef != null) {
            throw new IllegalArgumentException("平台表不能声明外部引用: " + code);
        }
        if (storageMode == StorageMode.PHYSICAL && !promotedColumns.isEmpty()) {
            throw new IllegalArgumentException("PHYSICAL 模式不允许声明提升列: " + code);
        }
        if (storageMode == StorageMode.JSON && !promotedColumns.isEmpty()) {
            throw new IllegalArgumentException("JSON 模式不允许声明提升列: " + code);
        }
        if (!promotedColumns.isEmpty()) {
            Set<String> fieldCodes = new HashSet<>();
            fields.forEach(field -> fieldCodes.add(field.code()));
            for (String column : promotedColumns) {
                if (!fieldCodes.contains(column)) {
                    throw new IllegalArgumentException(
                            "提升列 " + column + " 不存在于表 " + code + " 的字段集合");
                }
            }
        }
    }
}
