package io.github.ayongbox.daos.runtime.subform;

import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrForm;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.form.FormComponent;
import io.github.ayongbox.daos.core.metadata.form.FormField;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.app.AppNotPublishedException;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.exec.DataWriteService;
import io.github.ayongbox.daos.runtime.relation.RelationService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 明细表 / 子表单运行时。
 *
 * <p>子表通过「指向主表的关联字段」建立外键，该约定复用现有 relation 能力，
 * 不额外引入元数据。主子表写入共用同一事务；子表读写仍走
 * {@link DataWriteService}/{@link DataQueryService}，按子表资源独立裁决权限。</p>
 */
public final class SubformService {

    private static final Set<String> SYSTEM_COLUMNS = Set.of(
            "id", "tenant_id", "created_by", "created_at",
            "updated_by", "updated_at", "version", "deleted");

    private final SnapshotStore snapshotStore;
    private final DataAccessFacade dataAccess;
    private final DataQueryService queryService;
    private final DataWriteService writeService;
    private final RelationService relationService;
    private final SchemaNormalizer normalizer = new SchemaNormalizer();

    public SubformService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccess,
            DataQueryService queryService,
            DataWriteService writeService,
            RelationService relationService) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.dataAccess = Objects.requireNonNull(dataAccess, "dataAccess");
        this.queryService = Objects.requireNonNull(queryService, "queryService");
        this.writeService = Objects.requireNonNull(writeService, "writeService");
        this.relationService = Objects.requireNonNull(relationService, "relationService");
    }

    /**
     * 一次事务写入主表与全部子表行。
     */
    public String create(
            String appId,
            String formCode,
            Map<String, Object> values,
            Map<String, List<Map<String, Object>>> subforms,
            PermissionContext permissions) {

        Objects.requireNonNull(values, "values");
        Context context = context(appId, formCode);
        Map<String, List<Map<String, Object>>> rows = subforms == null ? Map.of() : subforms;
        validateSubformKeys(context, rows);
        for (Definition definition : context.definitions().values()) {
            validateRowCount(definition, rows.getOrDefault(definition.fieldId(), List.of()), true);
        }
        relationService.validate(appId, context.parentTable().source(), values, permissions);

        return dataAccess.inTransaction(() -> {
            String id = writeService.insert(appId, context.parentTable().tableId(), values, permissions);
            for (Definition definition : context.definitions().values()) {
                for (Map<String, Object> row : rows.getOrDefault(definition.fieldId(), List.of())) {
                    insertRow(appId, definition, row, id, permissions);
                }
            }
            return id;
        });
    }

    /**
     * 更新主表并按提交结果对账子表：已有行更新、无 id 新增、缺失行删除。
     */
    public int update(
            String appId,
            String formCode,
            String recordId,
            long version,
            Map<String, Object> values,
            Map<String, List<Map<String, Object>>> subforms,
            PermissionContext permissions) {

        Context context = context(appId, formCode);
        Map<String, List<Map<String, Object>>> rows = subforms == null ? Map.of() : subforms;
        validateSubformKeys(context, rows);
        boolean hasValues = values != null && !values.isEmpty();
        if (hasValues) {
            relationService.validate(appId, context.parentTable().source(), values, permissions);
        }
        rows.forEach((fieldId, items) ->
                validateRowCount(context.definitions().get(fieldId), items, false));

        return dataAccess.inTransaction(() -> {
            int affected = hasValues
                    ? writeService.update(appId, context.parentTable().tableId(), recordId, version, values, permissions)
                    : 1;
            for (Map.Entry<String, List<Map<String, Object>>> entry : rows.entrySet()) {
                reconcile(appId, context.definitions().get(entry.getKey()),
                        entry.getValue(), recordId, permissions);
            }
            return affected;
        });
    }

    /**
     * 读取主表记录与全部子表行，供运行端完整回显。
     */
    public FormRecord load(
            String appId, String formCode, String recordId, PermissionContext permissions) {

        Context context = context(appId, formCode);
        QueryRequest request = new QueryRequest(
                context.mainFields(),
                new FilterNode.Compare("id", ConditionOperator.EQ, List.of(recordId)),
                List.of(),
                1,
                1);
        List<Map<String, Object>> rows =
                queryService.query(appId, context.parentTable().tableId(), request, permissions);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("记录不存在或无权访问: " + recordId);
        }
        Map<String, List<Map<String, Object>>> subforms = new LinkedHashMap<>();
        for (Definition definition : context.definitions().values()) {
            subforms.put(definition.fieldId(), queryRows(appId, definition, recordId, permissions));
        }
        return new FormRecord(rows.get(0), subforms);
    }

    /**
     * 级联删除主记录及其全部子表行。
     */
    public int delete(
            String appId,
            String formCode,
            String recordId,
            long version,
            PermissionContext permissions) {

        Context context = context(appId, formCode);
        return dataAccess.inTransaction(() -> {
            for (Definition definition : context.definitions().values()) {
                deleteRows(appId, definition, recordId, permissions);
            }
            return writeService.delete(
                    appId, context.parentTable().tableId(), recordId, version, permissions);
        });
    }

    /**
     * 表维度删除：扫描所有引用该主表的表单并级联删除子表行，再删除主记录。
     */
    public int deleteWithChildren(
            String appId,
            String tableId,
            String recordId,
            long version,
            PermissionContext permissions) {

        SchemaSnapshot snapshot = requireActive(appId);
        NormalizedSchema schema = normalize(snapshot);
        IrTable parent = schema.requireTable(tableId);
        Map<String, Definition> definitions = new LinkedHashMap<>();
        for (SchemaForm form : snapshot.app().forms()) {
            if (form.deleted() || !form.tableId().equals(tableId)) {
                continue;
            }
            collectDefinitions(schema, parent, form.fields(), definitions);
        }

        return dataAccess.inTransaction(() -> {
            for (Definition definition : definitions.values()) {
                deleteRows(appId, definition, recordId, permissions);
            }
            return writeService.delete(appId, tableId, recordId, version, permissions);
        });
    }

    private void insertRow(
            String appId,
            Definition definition,
            Map<String, Object> row,
            String parentId,
            PermissionContext permissions) {

        Map<String, Object> payload = rowPayload(row);
        payload.put(definition.foreignKeyCode(), parentId);
        validateRelations(appId, definition, payload, permissions);
        writeService.insert(appId, definition.childTable().tableId(), payload, permissions);
    }

    private void reconcile(
            String appId,
            Definition definition,
            List<Map<String, Object>> submitted,
            String parentId,
            PermissionContext permissions) {

        List<Map<String, Object>> existing = queryRows(appId, definition, parentId, permissions);
        Map<String, Map<String, Object>> existingById = new LinkedHashMap<>();
        for (Map<String, Object> row : existing) {
            Object id = row.get("id");
            if (id != null) {
                existingById.put(String.valueOf(id), row);
            }
        }

        Set<String> kept = new LinkedHashSet<>();
        for (Map<String, Object> row : submitted) {
            Map<String, Object> payload = rowPayload(row);
            payload.put(definition.foreignKeyCode(), parentId);
            validateRelations(appId, definition, payload, permissions);

            Object rawId = row.get("id");
            if (rawId == null || String.valueOf(rawId).isBlank()) {
                if (!definition.allowAdd()) {
                    throw new IllegalArgumentException(
                            "子表单 " + definition.fieldId() + " 不允许新增行");
                }
                writeService.insert(appId, definition.childTable().tableId(), payload, permissions);
                continue;
            }

            String childId = String.valueOf(rawId);
            Map<String, Object> current = existingById.get(childId);
            if (current == null) {
                throw new IllegalArgumentException(
                        "子表记录不存在或不属于当前主记录: " + childId);
            }
            long childVersion = submittedVersion(row.get("version"), versionOf(current));
            writeService.update(
                    appId, definition.childTable().tableId(), childId, childVersion, payload, permissions);
            kept.add(childId);
        }

        for (Map.Entry<String, Map<String, Object>> entry : existingById.entrySet()) {
            if (kept.contains(entry.getKey())) {
                continue;
            }
            if (!definition.allowDelete()) {
                throw new IllegalArgumentException(
                        "子表单 " + definition.fieldId() + " 不允许删除行");
            }
            writeService.delete(
                    appId, definition.childTable().tableId(), entry.getKey(),
                    versionOf(entry.getValue()), permissions);
        }
    }

    private void deleteRows(
            String appId, Definition definition, String parentId, PermissionContext permissions) {
        for (Map<String, Object> row : queryRows(appId, definition, parentId, permissions)) {
            Object id = row.get("id");
            if (id == null) {
                continue;
            }
            writeService.delete(
                    appId, definition.childTable().tableId(), String.valueOf(id),
                    versionOf(row), permissions);
        }
    }

    private List<Map<String, Object>> queryRows(
            String appId, Definition definition, String parentId, PermissionContext permissions) {

        QueryRequest request = new QueryRequest(
                definition.fieldCodes(),
                new FilterNode.Compare(
                        definition.foreignKeyCode(), ConditionOperator.EQ, List.of(parentId)),
                List.of(),
                1,
                Math.min(Math.max(definition.maxRows(), 1), QueryRequest.MAX_PAGE_SIZE));
        return queryService.query(appId, definition.childTable().tableId(), request, permissions);
    }

    private void validateRelations(
            String appId,
            Definition definition,
            Map<String, Object> payload,
            PermissionContext permissions) {

        Map<String, Object> validation = new LinkedHashMap<>(payload);
        validation.remove(definition.foreignKeyCode());
        relationService.validate(appId, definition.childTable().source(), validation, permissions);
    }

    private Map<String, Object> rowPayload(Map<String, Object> row) {
        if (row == null) {
            throw new IllegalArgumentException("子表单行不能为空");
        }
        Map<String, Object> payload = new LinkedHashMap<>(row);
        payload.keySet().removeIf(SYSTEM_COLUMNS::contains);
        return payload;
    }

    private void validateSubformKeys(
            Context context, Map<String, List<Map<String, Object>>> subforms) {
        for (String fieldId : subforms.keySet()) {
            if (!context.definitions().containsKey(fieldId)) {
                throw new IllegalArgumentException("表单没有对应的子表单: " + fieldId);
            }
        }
    }

    private void validateRowCount(
            Definition definition, List<Map<String, Object>> rows, boolean creating) {
        int count = rows == null ? 0 : rows.size();
        if (count < definition.minRows()) {
            throw new IllegalArgumentException(
                    "子表单 " + definition.fieldId() + " 至少需要 " + definition.minRows() + " 行");
        }
        if (count > definition.maxRows()) {
            throw new IllegalArgumentException(
                    "子表单 " + definition.fieldId() + " 最多允许 " + definition.maxRows() + " 行");
        }
        if (creating && !definition.allowAdd() && count > 0) {
            throw new IllegalArgumentException(
                    "子表单 " + definition.fieldId() + " 不允许新增行");
        }
    }

    private Context context(String appId, String formCode) {
        SchemaSnapshot snapshot = requireActive(appId);
        NormalizedSchema schema = normalize(snapshot);
        IrForm form = schema.requireFormByCode(formCode);
        IrTable parent = schema.requireTable(form.tableId());

        Map<String, Definition> definitions = new LinkedHashMap<>();
        collectDefinitions(schema, parent, form.source().fields(), definitions);
        List<String> mainFields = new ArrayList<>();
        collectMainFields(parent, form.source().fields(), mainFields);
        return new Context(parent, definitions, mainFields);
    }

    private NormalizedSchema normalize(SchemaSnapshot snapshot) {
        return normalizer.normalize(
                snapshot.appCode(),
                snapshot.snapshotId(),
                snapshot.releaseVersion(),
                snapshot.app().tables(),
                snapshot.app().forms());
    }

    private void collectDefinitions(
            NormalizedSchema schema,
            IrTable parent,
            List<FormField> fields,
            Map<String, Definition> definitions) {

        for (FormField field : fields) {
            if (field.component() == FormComponent.SUBFORM) {
                Definition definition = definition(schema, parent, field);
                definitions.putIfAbsent(definition.fieldId(), definition);
                continue;
            }
            collectDefinitions(schema, parent, field.children(), definitions);
        }
    }

    private Definition definition(NormalizedSchema schema, IrTable parent, FormField field) {
        if (field.subformTableId() == null || field.subformTableId().isBlank()) {
            throw new IllegalArgumentException("子表单 " + field.id() + " 未绑定子表");
        }
        IrTable child = schema.requireTable(field.subformTableId());
        IrColumn foreignKey = foreignKey(child, parent);

        List<String> fieldCodes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (FormField column : field.subformFields()) {
            if (column.fieldId() == null) {
                continue;
            }
            IrColumn irColumn = child.columnByFieldId(column.fieldId());
            if (irColumn != null && seen.add(irColumn.fieldCode())) {
                fieldCodes.add(irColumn.fieldCode());
            }
        }
        return new Definition(
                field.id(), child, foreignKey.fieldCode(), fieldCodes,
                Math.max(0, field.minRows()), field.maxRows(),
                field.allowAdd(), field.allowDelete());
    }

    private IrColumn foreignKey(IrTable child, IrTable parent) {
        List<IrColumn> candidates = child.columns().stream()
                .filter(column -> !column.source().deleted())
                .filter(column -> column.source().logicalType() == FieldLogicalType.RELATION)
                .filter(column -> column.source().config() instanceof FieldConfig.RelationConfig config
                        && parent.tableId().equals(config.targetTableId()))
                .toList();
        if (candidates.size() != 1) {
            throw new IllegalArgumentException(
                    "子表 " + child.tableCode() + " 需要且只能有一个指向主表 "
                            + parent.tableCode() + " 的关联字段作为外键");
        }
        IrColumn foreignKey = candidates.get(0);
        if (!foreignKey.hasPhysicalColumn()) {
            throw new IllegalArgumentException(
                    "子表 " + child.tableCode() + " 的外键字段不支持物理写入: "
                            + foreignKey.fieldCode());
        }
        return foreignKey;
    }

    private void collectMainFields(IrTable parent, List<FormField> fields, List<String> codes) {
        for (FormField field : fields) {
            if (field.component() == FormComponent.SUBFORM) {
                continue;
            }
            if (field.fieldId() != null) {
                IrColumn column = parent.columnByFieldId(field.fieldId());
                if (column != null) {
                    codes.add(column.fieldCode());
                }
            }
            collectMainFields(parent, field.children(), codes);
        }
    }

    private long versionOf(Map<String, Object> row) {
        Object version = row.get("version");
        return version instanceof Number number ? number.longValue() : 0L;
    }

    private long submittedVersion(Object submitted, long fallback) {
        if (submitted instanceof Number number) {
            return number.longValue();
        }
        if (submitted instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private SchemaSnapshot requireActive(String appId) {
        Objects.requireNonNull(appId, "appId");
        return snapshotStore.findActive(appId)
                .orElseThrow(() -> new AppNotPublishedException("应用还没有生效版本: " + appId));
    }

    private record Context(
            IrTable parentTable,
            Map<String, Definition> definitions,
            List<String> mainFields) {
    }

    private record Definition(
            String fieldId,
            IrTable childTable,
            String foreignKeyCode,
            List<String> fieldCodes,
            int minRows,
            int maxRows,
            boolean allowAdd,
            boolean allowDelete) {
    }

    /**
     * 回显结果：主表记录 + 按子表单表单项 id 分组的子表行。
     */
    public record FormRecord(
            Map<String, Object> record,
            Map<String, List<Map<String, Object>>> subforms) {
    }
}
