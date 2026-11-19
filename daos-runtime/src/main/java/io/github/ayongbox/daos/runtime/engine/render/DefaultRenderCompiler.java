package io.github.ayongbox.daos.runtime.engine.render;

import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrForm;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.RenderTree;
import io.github.ayongbox.daos.core.ir.RenderTree.RenderNode;
import io.github.ayongbox.daos.core.metadata.common.FormScene;
import io.github.ayongbox.daos.core.metadata.form.FormComponent;
import io.github.ayongbox.daos.core.metadata.form.FormField;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.MaskRule;
import io.github.ayongbox.daos.core.pipeline.render.RenderCompiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 默认渲染树编译器。只读归一化 IR，输出与权限快照绑定的渲染树。
 *
 * <p>编译期完成全部裁剪：隐藏字段不进入产物，只读与必填已合并权限快照，
 * 脱敏规则写入节点属性供前端展示。运行期与前端都不允许再次放宽权限。</p>
 */
public final class DefaultRenderCompiler implements RenderCompiler {

    @Override
    public RenderTree compile(
            NormalizedSchema schema,
            String formId,
            FormScene scene,
            FieldMask fieldMask) {

        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(formId, "formId");

        FormScene resolvedScene = scene == null ? FormScene.VIEW : scene;
        FieldMask mask = fieldMask == null ? FieldMask.empty() : fieldMask;

        IrForm form = schema.requireForm(formId);
        IrTable table = schema.requireTable(form.tableId());
        List<RenderNode> nodes = renderFields(schema, table, form.source().fields(), mask);

        return new RenderTree(
                formId,
                resolvedScene,
                form.source().schemaVersion(),
                nodes,
                Map.of("linkages", form.source().linkages()));
    }

    private List<RenderNode> renderFields(
            NormalizedSchema schema,
            IrTable table,
            List<FormField> fields,
            FieldMask mask) {

        List<RenderNode> nodes = new ArrayList<>(fields.size());
        for (FormField field : fields) {
            RenderNode node = renderField(schema, table, field, mask);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private RenderNode renderField(
            NormalizedSchema schema,
            IrTable table,
            FormField field,
            FieldMask mask) {

        String fieldId = field.fieldId();
        if (field.hidden() || (fieldId != null && mask.hidden(fieldId))) {
            return null;
        }

        IrColumn column = fieldId == null ? null : table.columnByFieldId(fieldId);
        boolean readonly = field.readonly() || (fieldId != null && mask.readonly(fieldId));
        boolean required = fieldId != null
                && (mask.requiredFieldIds().contains(fieldId)
                || Boolean.TRUE.equals(field.requiredOverride())
                || (column != null && column.source().constraints().required()));

        boolean subform = field.component() == FormComponent.SUBFORM;
        List<RenderNode> children;
        if (subform) {
            IrTable subformTable = schema.requireTable(field.subformTableId());
            children = renderFields(schema, subformTable, field.subformFields(), mask);
        } else {
            children = renderFields(schema, table, field.children(), mask);
        }

        if (field.component().container() && children.isEmpty()) {
            return null;
        }

        Map<String, Object> props = propsOf(field, fieldId, mask, column);
        if (subform) {
            IrTable subformTable = schema.requireTable(field.subformTableId());
            props.put("subform", true);
            props.put("tableCode", subformTable.tableCode());
            props.put("minRows", field.minRows());
            props.put("maxRows", field.maxRows());
            props.put("allowAdd", field.allowAdd());
            props.put("allowDelete", field.allowDelete());
            props.put("allowSort", field.allowSort());
            props.put("showRowNumber", field.showRowNumber());
            props.put("showSummary", field.showSummary());
            props.put("summaryFields", field.summaryFields());
        }

        String label = labelOf(field, column);
        return new RenderNode(
                field.id(),
                field.component().code(),
                column == null ? null : column.fieldCode(),
                label,
                field.span(),
                readonly,
                required,
                false,
                props,
                children);
    }

    private String labelOf(FormField field, IrColumn column) {
        if (field.label() != null && !field.label().isBlank()) {
            return field.label();
        }
        return column == null ? null : column.source().displayName();
    }

    private Map<String, Object> propsOf(
            FormField field, String fieldId, FieldMask mask, IrColumn column) {
        Map<String, Object> props = new LinkedHashMap<>(field.props());
        putIfPresent(props, "fieldId", fieldId);
        putIfPresent(props, "placeholder", field.placeholder());
        putIfPresent(props, "helpText", field.helpText());
        putIfPresent(props, "tooltip", field.tooltip());
        if (field.defaultValue() != null) {
            props.put("defaultValue", field.defaultValue());
        }
        if (column != null
                && column.source().config() instanceof FieldConfig.RelationConfig relation) {
            props.put("relation", true);
            props.put("multiple", relation.multiple());
            props.put("maxSelected", relation.maxSelected());
        }
        if (column != null && column.source().config() instanceof FieldConfig.SelectConfig select) {
            props.put("options", optionProps(select.options()));
        }
        if (column != null
                && column.source().config() instanceof FieldConfig.MultiSelectConfig multi) {
            props.put("options", optionProps(multi.options()));
        }
        if (fieldId != null) {
            maskRuleOf(mask, fieldId).ifPresent(rule -> putMaskRule(props, rule));
        }
        return props;
    }

    private List<Map<String, Object>> optionProps(List<io.github.ayongbox.daos.core.metadata.field.OptionItem> options) {
        if (options == null) return List.of();
        return options.stream()
                .filter(option -> option.enabled())
                .map(option -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", option.label());
                    item.put("value", option.code());
                    return item;
                })
                .toList();
    }

    private Optional<MaskRule> maskRuleOf(FieldMask mask, String fieldId) {
        return mask.maskRules().stream()
                .filter(rule -> rule.fieldId().equals(fieldId))
                .findFirst();
    }

    private void putMaskRule(Map<String, Object> props, MaskRule rule) {
        props.put("maskType", rule.type().name());
        putIfPresent(props, "maskPattern", rule.pattern());
        putIfPresent(props, "maskReplacement", rule.replacement());
    }

    private void putIfPresent(Map<String, Object> props, String key, String value) {
        if (value != null && !value.isBlank()) {
            props.put(key, value);
        }
    }
}
