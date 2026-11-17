package io.github.ayongbox.daos.core.metadata.form;

import io.github.ayongbox.daos.core.metadata.common.FormScene;

import java.util.Objects;

/**
 * 场景布局。新建、编辑、查看三场景各自独立。
 */
public record FormLayout(
        FormScene scene,
        int columns,
        String labelPosition,
        int labelWidth,
        String labelAlign,
        String layoutMode,
        String size,
        int gutter,
        boolean dense,
        String width,
        int maxWidth,
        boolean showSubmit,
        boolean showReset,
        String submitText,
        String resetText,
        String redirectAfterSubmit,
        boolean subformSummary) {

    public FormLayout {
        Objects.requireNonNull(scene, "scene");
        columns = columns == 0 ? 24 : columns;
        if (columns != 24) {
            throw new IllegalArgumentException("栅格列数固定为 24");
        }
        labelPosition = labelPosition == null ? "top" : labelPosition;
        labelWidth = labelWidth == 0 ? 120 : labelWidth;
        labelAlign = labelAlign == null ? "right" : labelAlign;
        layoutMode = layoutMode == null ? "horizontal" : layoutMode;
        size = size == null ? "middle" : size;
        gutter = gutter == 0 ? 16 : gutter;
        width = width == null ? "100%" : width;
        maxWidth = maxWidth == 0 ? 960 : maxWidth;
        submitText = submitText == null ? "提交" : submitText;
        resetText = resetText == null ? "重置" : resetText;
    }

    public static FormLayout defaults(FormScene scene) {
        return new FormLayout(scene, 24, "top", 120, "right", "horizontal", "middle", 16,
                false, "100%", 960, true, false, "提交", "重置", null, true);
    }
}
