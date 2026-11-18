package io.github.ayongbox.daos.runtime.relation;

import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.app.AppNotPublishedException;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriteException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 关联字段运行时：为目标表提供选择项，并在写入前校验关联记录存在且可读。
 *
 * <p>所有读取都走 {@link DataQueryService}，因此租户隔离、行级权限与字段掩码
 * 与普通查询完全一致；目标表不可读时选择项为空，写入直接拒绝。</p>
 */
public final class RelationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final SnapshotStore snapshotStore;
    private final DataQueryService queryService;

    public RelationService(SnapshotStore snapshotStore, DataQueryService queryService) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
    }

    /**
     * 查询关联选择项。关键字匹配目标表的展示字段。
     */
    public List<RelationOption> options(
            String appId,
            String tableCode,
            String fieldCode,
            String keyword,
            int limit,
            PermissionContext permissions) {

        SchemaSnapshot snapshot = requireActive(appId);
        SchemaTable source = requireTable(snapshot, tableCode);
        SchemaField field = requireRelationField(source, fieldCode);
        FieldConfig.RelationConfig config = (FieldConfig.RelationConfig) field.config();
        SchemaTable target = requireTable(snapshot, config.targetTableId());
        SchemaField displayField = displayField(target, config);

        List<String> fields = displayField == null ? List.of() : List.of(displayField.code());
        FilterNode filter = displayField != null && keyword != null && !keyword.isBlank()
                ? new FilterNode.Compare(displayField.code(), ConditionOperator.CONTAINS,
                        List.of(keyword.trim()))
                : FilterNode.alwaysTrue();
        QueryRequest request = new QueryRequest(
                fields, filter, List.of(), 1, clamp(limit), List.of(), List.of());
        try {
            return queryService.query(appId, target.id(), request, permissions).stream()
                    .map(row -> new RelationOption(
                            String.valueOf(row.get("id")),
                            labelOf(row, displayField)))
                    .toList();
        } catch (AccessDeniedException | PermissionRewriteException error) {
            return List.of();
        }
    }

    /**
     * 写入前校验关联值。超出权限或不存在的目标统一按「关联记录不存在或无权访问」拒绝。
     */
    public void validate(
            String appId,
            SchemaTable source,
            Map<String, Object> values,
            PermissionContext permissions) {

        SchemaSnapshot snapshot = requireActive(appId);
        for (SchemaField field : source.fields()) {
            if (field.deleted() || field.logicalType() != FieldLogicalType.RELATION
                    || !(field.config() instanceof FieldConfig.RelationConfig config)) {
                continue;
            }
            if (!values.containsKey(field.code())) {
                continue;
            }
            List<String> ids = relationIds(values.get(field.code()));
            if (ids.isEmpty()) {
                continue;
            }
            if (!config.multiple() && ids.size() > 1) {
                throw new IllegalArgumentException("关联字段只允许选择一个值: " + field.code());
            }
            if (ids.size() > MAX_LIMIT) {
                throw new IllegalArgumentException("关联选择最多 " + MAX_LIMIT + " 项: " + field.code());
            }
            SchemaTable target = requireTable(snapshot, config.targetTableId());
            Set<String> found = existingIds(appId, target, ids, permissions);
            if (!found.containsAll(ids)) {
                throw new IllegalArgumentException("关联记录不存在或无权访问: " + field.code());
            }
        }
    }

    private Set<String> existingIds(
            String appId,
            SchemaTable target,
            List<String> ids,
            PermissionContext permissions) {

        QueryRequest request = new QueryRequest(
                List.of(),
                new FilterNode.Compare("id", ConditionOperator.IN, new ArrayList<>(ids)),
                List.of(),
                1,
                ids.size(),
                List.of(),
                List.of());
        try {
            Set<String> found = new LinkedHashSet<>();
            queryService.query(appId, target.id(), request, permissions)
                    .forEach(row -> found.add(String.valueOf(row.get("id"))));
            return found;
        } catch (AccessDeniedException | PermissionRewriteException error) {
            throw new IllegalArgumentException("关联记录不存在或无权访问: " + target.code(), error);
        }
    }

    private SchemaSnapshot requireActive(String appId) {
        Objects.requireNonNull(appId, "appId");
        return snapshotStore.findActive(appId)
                .orElseThrow(() -> new AppNotPublishedException("应用还没有生效版本: " + appId));
    }

    private SchemaTable requireTable(SchemaSnapshot snapshot, String tableIdOrCode) {
        return snapshot.app().tables().stream()
                .filter(table -> !table.deleted())
                .filter(table -> table.id().equals(tableIdOrCode) || table.code().equals(tableIdOrCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据表不存在: " + tableIdOrCode));
    }

    private SchemaField requireRelationField(SchemaTable table, String fieldCode) {
        return table.fields().stream()
                .filter(field -> !field.deleted() && field.code().equals(fieldCode))
                .findFirst()
                .filter(field -> field.logicalType() == FieldLogicalType.RELATION
                        && field.config() instanceof FieldConfig.RelationConfig)
                .orElseThrow(() -> new IllegalArgumentException(
                        "关联字段不存在或未配置关联: " + fieldCode));
    }

    private SchemaField displayField(SchemaTable target, FieldConfig.RelationConfig config) {
        if (config.displayFieldId() != null) {
            return target.fields().stream()
                    .filter(field -> !field.deleted())
                    .filter(field -> field.id().equals(config.displayFieldId()))
                    .findFirst()
                    .orElse(null);
        }
        if (target.nameFieldId() != null) {
            return target.fields().stream()
                    .filter(field -> !field.deleted())
                    .filter(field -> field.id().equals(target.nameFieldId()))
                    .findFirst()
                    .orElse(null);
        }
        return target.fields().stream()
                .filter(field -> !field.deleted())
                .filter(field -> field.logicalType() == FieldLogicalType.TEXT_SHORT
                        || field.logicalType() == FieldLogicalType.TEXT_LONG)
                .findFirst()
                .orElse(null);
    }

    private String labelOf(Map<String, Object> row, SchemaField displayField) {
        Object label = displayField == null ? null : row.get(displayField.code());
        return label == null ? String.valueOf(row.get("id")) : String.valueOf(label);
    }

    private int clamp(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<String> relationIds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof Collection<?> values) {
            return values.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            String body = text.substring(1, text.length() - 1).trim();
            if (body.isEmpty()) {
                return List.of();
            }
            return java.util.Arrays.stream(body.split(","))
                    .map(item -> item.trim().replaceAll("^\"|\"$", ""))
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        return List.of(text);
    }

    public record RelationOption(String value, String label) {
    }
}
