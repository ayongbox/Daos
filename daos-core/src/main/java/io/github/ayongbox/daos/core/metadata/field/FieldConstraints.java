package io.github.ayongbox.daos.core.metadata.field;

/**
 * 字段结构约束。
 */
public record FieldConstraints(
        boolean required,
        boolean unique,
        boolean indexed,
        boolean searchable,
        boolean sortable,
        boolean filterable,
        boolean immutable,
        boolean caseSensitive,
        boolean trimWhitespace,
        Object defaultValue,
        String defaultValueExpression,
        boolean deprecated,
        String deprecatedAt) {

    public static FieldConstraints defaults() {
        return new FieldConstraints(false, false, false, false, false, true, false, false, true, null, null, false, null);
    }
}
