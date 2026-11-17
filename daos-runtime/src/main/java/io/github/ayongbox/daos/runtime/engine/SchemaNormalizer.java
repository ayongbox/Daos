package io.github.ayongbox.daos.runtime.engine;

import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrForm;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.form.FormField;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Schema 归一化器。把草稿或发布快照转换为三路编译器唯一可消费的 IR。
 *
 * <p>职责边界：只做结构决策，不生成任何 SQL，不访问数据库。
 * 同一输入必须产出逐字节一致的 {@link NormalizedSchema}。</p>
 *
 * <p>派生字段依赖的提取规则：</p>
 * <ul>
 *   <li>{@code rollup}：依赖 {@link FieldConfig.RollupConfig#relationFieldId()} 指向的来源字段</li>
 *   <li>{@code formula}：从表达式中提取形如 <code>{fieldCode}</code> 的同表字段引用</li>
 * </ul>
 */
public final class SchemaNormalizer {

    public static final int MAX_TABLES_PER_APP = 500;

    private static final int MAX_PHYSICAL_NAME_LENGTH = 64;
    private static final Pattern TABLE_CODE = Pattern.compile("[a-z][a-z0-9_]{0,39}");
    private static final Pattern APP_CODE = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final Pattern FORMULA_FIELD_REF = Pattern.compile("\\{([a-z][a-z0-9_]{0,39})}");

    public NormalizedSchema normalize(
            String appCode,
            String snapshotId,
            long releaseVersion,
            List<SchemaTable> tables) {

        return normalize(appCode, snapshotId, releaseVersion, tables, List.of());
    }

    public NormalizedSchema normalize(
            String appCode,
            String snapshotId,
            long releaseVersion,
            List<SchemaTable> tables,
            List<SchemaForm> forms) {

        Objects.requireNonNull(appCode, "appCode");
        Objects.requireNonNull(snapshotId, "snapshotId");

        if (!APP_CODE.matcher(appCode).matches()) {
            throw new SchemaNormalizeException("应用编码不合法: " + appCode + "，要求 [a-z][a-z0-9_]{0,31}");
        }
        if (snapshotId.isBlank()) {
            throw new SchemaNormalizeException("快照标识不能为空");
        }
        if (tables == null || tables.isEmpty()) {
            throw new SchemaNormalizeException("应用 " + appCode + " 没有任何表");
        }
        if (tables.size() > MAX_TABLES_PER_APP) {
            throw new SchemaNormalizeException(
                    "单应用表数超过上限 " + MAX_TABLES_PER_APP + "，当前 " + tables.size());
        }

        String appId = tables.get(0).appId();
        Map<String, IrTable> tablesById = new LinkedHashMap<>();
        Map<String, IrTable> tablesByCode = new LinkedHashMap<>();
        Set<String> physicalNames = new HashSet<>();

        for (SchemaTable table : tables) {
            validateTable(appId, table);

            String physicalName = physicalName(appCode, table.code());
            if (!physicalNames.add(physicalName)) {
                throw new SchemaNormalizeException("物理表名重复: " + physicalName);
            }
            if (tablesByCode.containsKey(table.code())) {
                throw new SchemaNormalizeException("表编码重复: " + table.code());
            }

            IrTable irTable = buildTable(table, physicalName);
            tablesById.put(table.id(), irTable);
            tablesByCode.put(table.code(), irTable);
        }

        validateRelations(tablesById);

        Map<String, IrForm> formsById = new LinkedHashMap<>();
        Map<String, IrForm> formsByCode = new LinkedHashMap<>();
        validateForms(appId, tablesById, forms, formsById, formsByCode);

        return new NormalizedSchema(
                appId, appCode, snapshotId, releaseVersion,
                tablesById, tablesByCode, formsById, formsByCode, sortDerivedFields(tables));
    }

    private void validateTable(String appId, SchemaTable table) {
        if (!Objects.equals(appId, table.appId())) {
            throw new SchemaNormalizeException(
                    "表 " + table.code() + " 的 appId 与应用不一致: " + table.appId());
        }
        if (!TABLE_CODE.matcher(table.code()).matches()) {
            throw new SchemaNormalizeException(
                    "表编码不合法: " + table.code() + "，要求 [a-z][a-z0-9_]{0,39}");
        }

        Set<String> fieldCodes = new HashSet<>();
        for (SchemaField field : table.fields()) {
            if (!Objects.equals(table.id(), field.tableId())) {
                throw new SchemaNormalizeException(
                        "字段 " + field.code() + " 的 tableId 与所属表不一致");
            }
            if (!fieldCodes.add(field.code())) {
                throw new SchemaNormalizeException(
                        "表 " + table.code() + " 存在重复字段编码: " + field.code());
            }
        }

        if (table.nameFieldId() != null && table.fields().stream()
                .noneMatch(field -> field.id().equals(table.nameFieldId()))) {
            throw new SchemaNormalizeException(
                    "表 " + table.code() + " 的标题字段不存在: " + table.nameFieldId());
        }
    }

    private String physicalName(String appCode, String tableCode) {
        String physicalName = "app_" + appCode + "_" + tableCode;
        if (physicalName.length() > MAX_PHYSICAL_NAME_LENGTH) {
            throw new SchemaNormalizeException(
                    "物理表名超过 " + MAX_PHYSICAL_NAME_LENGTH + " 字符: " + physicalName);
        }
        return physicalName;
    }

    private IrTable buildTable(SchemaTable table, String physicalName) {
        List<IrColumn> columns = new ArrayList<>(table.fields().size());
        Map<String, IrColumn> byId = new LinkedHashMap<>();
        Map<String, IrColumn> byCode = new LinkedHashMap<>();

        for (SchemaField field : table.fields()) {
            IrColumn column = new IrColumn(field, field.code(), storageOf(field, table));
            columns.add(column);
            byId.put(column.fieldId(), column);
            byCode.put(column.fieldCode(), column);
        }
        return new IrTable(table, physicalName, columns, byId, byCode);
    }

    private IrColumn.Storage storageOf(SchemaField field, SchemaTable table) {
        if (field.logicalType().computed()) {
            return IrColumn.Storage.DERIVED;
        }
        return switch (table.storageMode()) {
            case PHYSICAL -> IrColumn.Storage.COLUMN;
            case HYBRID -> table.promotedColumns().contains(field.code())
                    ? IrColumn.Storage.PROMOTED_JSON
                    : IrColumn.Storage.JSON_MEMBER;
            case JSON -> IrColumn.Storage.JSON_MEMBER;
        };
    }

    private void validateRelations(Map<String, IrTable> tablesById) {
        for (IrTable table : tablesById.values()) {
            for (RelationDef relation : table.source().relations()) {
                IrTable target = tablesById.get(relation.targetTableId());
                if (target == null) {
                    throw new SchemaNormalizeException(
                            "表 " + table.tableCode() + " 的关联 " + relation.code()
                                    + " 指向不存在的表: " + relation.targetTableId());
                }
                if (relation.sourceFieldId() != null) {
                    requireField(table, relation.sourceFieldId(), relation.code(), "源字段");
                }
                if (relation.targetFieldId() != null) {
                    requireField(target, relation.targetFieldId(), relation.code(), "目标字段");
                }
            }
        }
    }

    private void requireField(IrTable table, String fieldId, String relationCode, String role) {
        if (table.columnByFieldId(fieldId) == null) {
            throw new SchemaNormalizeException(
                    "关联 " + relationCode + " 的" + role + "不存在于表 " + table.tableCode() + ": " + fieldId);
        }
    }

    private void validateForms(
            String appId,
            Map<String, IrTable> tablesById,
            List<SchemaForm> forms,
            Map<String, IrForm> formsById,
            Map<String, IrForm> formsByCode) {

        if (forms == null) {
            return;
        }
        for (SchemaForm form : forms) {
            if (!Objects.equals(appId, form.appId())) {
                throw new SchemaNormalizeException(
                        "表单 " + form.code() + " 的 appId 与应用不一致: " + form.appId());
            }
            IrTable table = tablesById.get(form.tableId());
            if (table == null) {
                throw new SchemaNormalizeException(
                        "表单 " + form.code() + " 指向不存在的表: " + form.tableId());
            }
            if (formsById.containsKey(form.id())) {
                throw new SchemaNormalizeException("表单 id 重复: " + form.id());
            }
            if (formsByCode.containsKey(form.code())) {
                throw new SchemaNormalizeException("表单编码重复: " + form.code());
            }

            Set<String> formFieldIds = new HashSet<>();
            validateFormFields(table, tablesById, form.code(), form.fields(), formFieldIds);
            IrForm irForm = new IrForm(form, table.tableCode());
            formsById.put(form.id(), irForm);
            formsByCode.put(form.code(), irForm);
        }
    }

    private void validateFormFields(
            IrTable table,
            Map<String, IrTable> tablesById,
            String formCode,
            List<FormField> fields,
            Set<String> formFieldIds) {

        for (FormField field : fields) {
            if (!formFieldIds.add(field.id())) {
                throw new SchemaNormalizeException(
                        "表单 " + formCode + " 存在重复表单项 id: " + field.id());
            }
            if (field.fieldId() != null && table.columnByFieldId(field.fieldId()) == null) {
                throw new SchemaNormalizeException(
                        "表单 " + formCode + " 的表单项 " + field.id()
                                + " 指向表 " + table.tableCode() + " 中不存在的字段: " + field.fieldId());
            }
            if (!field.subformFields().isEmpty()) {
                IrTable subformTable = tablesById.get(field.subformTableId());
                if (subformTable == null) {
                    throw new SchemaNormalizeException(
                            "表单 " + formCode + " 的子表单 " + field.id()
                                    + " 指向不存在的表: " + field.subformTableId());
                }
                validateFormFields(subformTable, tablesById, formCode, field.subformFields(), formFieldIds);
            }
            validateFormFields(table, tablesById, formCode, field.children(), formFieldIds);
        }
    }

    /**
     * 按表内依赖做拓扑排序。跨表引用不参与排序，因为物化与重算以表为单位调度。
     */
    private List<String> sortDerivedFields(List<SchemaTable> tables) {
        List<String> ordered = new ArrayList<>();

        for (SchemaTable table : tables) {
            Map<String, SchemaField> byCode = new HashMap<>();
            Map<String, SchemaField> byId = new HashMap<>();
            table.fields().forEach(field -> {
                byCode.put(field.code(), field);
                byId.put(field.id(), field);
            });

            Map<String, Set<String>> graph = new LinkedHashMap<>();
            for (SchemaField field : table.fields()) {
                if (!field.logicalType().computed()) {
                    continue;
                }
                graph.put(field.id(), dependenciesOf(field, byId, byCode));
            }
            if (graph.isEmpty()) {
                continue;
            }
            ordered.addAll(topologicalSort(graph, table.code()));
        }
        return ordered;
    }

    private Set<String> dependenciesOf(
            SchemaField field,
            Map<String, SchemaField> byId,
            Map<String, SchemaField> byCode) {

        Set<String> dependencies = new LinkedHashSet<>();

        if (field.config() instanceof FieldConfig.RollupConfig rollup && rollup.relationFieldId() != null) {
            SchemaField source = byId.get(rollup.relationFieldId());
            if (source != null) {
                dependencies.add(source.id());
            }
        }

        if (field.config() instanceof FieldConfig.FormulaConfig formula && formula.expression() != null) {
            Matcher matcher = FORMULA_FIELD_REF.matcher(formula.expression());
            while (matcher.find()) {
                SchemaField referenced = byCode.get(matcher.group(1));
                if (referenced != null && !referenced.id().equals(field.id())) {
                    dependencies.add(referenced.id());
                }
            }
        }
        return dependencies;
    }

    private List<String> topologicalSort(Map<String, Set<String>> graph, String tableCode) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();

        graph.keySet().forEach(node -> indegree.put(node, 0));
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            for (String dependency : entry.getValue()) {
                if (!indegree.containsKey(dependency)) {
                    continue;
                }
                outgoing.computeIfAbsent(dependency, key -> new ArrayList<>()).add(entry.getKey());
                indegree.merge(entry.getKey(), 1, Integer::sum);
            }
        }

        List<String> queue = new ArrayList<>();
        indegree.forEach((node, degree) -> {
            if (degree == 0) {
                queue.add(node);
            }
        });

        List<String> ordered = new ArrayList<>(indegree.size());
        for (int index = 0; index < queue.size(); index++) {
            String node = queue.get(index);
            ordered.add(node);
            for (String next : outgoing.getOrDefault(node, List.of())) {
                if (indegree.merge(next, -1, Integer::sum) == 0) {
                    queue.add(next);
                }
            }
        }

        if (ordered.size() != indegree.size()) {
            List<String> cycle = indegree.keySet().stream().filter(node -> !ordered.contains(node)).toList();
            throw new SchemaNormalizeException(
                    "表 " + tableCode + " 的派生字段存在循环依赖: " + cycle);
        }
        return ordered;
    }
}
