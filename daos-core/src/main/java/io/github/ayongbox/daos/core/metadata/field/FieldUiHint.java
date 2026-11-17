package io.github.ayongbox.daos.core.metadata.field;

/**
 * 字段展示提示，列表与详情共用。
 */
public record FieldUiHint(
        String placeholder,
        String helpText,
        String tooltip,
        String prefix,
        String suffix,
        Integer width,
        String align,
        boolean hiddenInList,
        boolean hiddenInDetail,
        boolean readonlyInForm) {

    public static FieldUiHint empty() {
        return new FieldUiHint(null, null, null, null, null, null, "left", false, false, false);
    }
}
