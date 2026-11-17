package io.github.ayongbox.daos.core.metadata.form;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表单项。容器节点（group / tab / grid）通过 children 嵌套，子表单通过 subformFields 定义列。
 */
public record FormField(
        String id,
        String fieldId,
        FormComponent component,
        String label,
        int span,
        int offset,
        int sortOrder,
        String placeholder,
        String helpText,
        String tooltip,
        boolean readonly,
        boolean hidden,
        boolean disabled,
        Boolean requiredOverride,
        Object defaultValue,
        Map<String, Object> props,
        Map<String, String> style,
        List<FormField> children,
        String subformTableId,
        List<FormField> subformFields,
        int minRows,
        int maxRows,
        boolean allowAdd,
        boolean allowDelete,
        boolean allowSort,
        boolean showRowNumber,
        boolean showSummary,
        Map<String, String> summaryFields,
        String tabKey,
        String tabLabel,
        boolean collapsible,
        boolean defaultCollapsed,
        int columnCount) {

    public FormField {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(component, "component");
        if (!component.layoutOnly() && component != FormComponent.SUBFORM) {
            Objects.requireNonNull(fieldId, "fieldId");
        }
        if (span < 1 || span > 24) {
            throw new IllegalArgumentException("表单项 " + id + " 的 span 必须在 1-24 之间");
        }
        props = props == null ? Map.of() : Map.copyOf(props);
        style = style == null ? Map.of() : Map.copyOf(style);
        children = children == null ? List.of() : List.copyOf(children);
        subformFields = subformFields == null ? List.of() : List.copyOf(subformFields);
        summaryFields = summaryFields == null ? Map.of() : Map.copyOf(summaryFields);
        if (maxRows == 0) {
            maxRows = 200;
        }
        if (columnCount == 0) {
            columnCount = 2;
        }
    }
}
