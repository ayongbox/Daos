package io.github.ayongbox.daos.core.metadata.permission;

import java.util.List;

/**
 * 字段级权限。hidden 优先于 readonly。
 */
public record FieldMask(
        List<String> hiddenFieldIds,
        List<String> readonlyFieldIds,
        List<String> requiredFieldIds,
        List<MaskRule> maskRules) {

    public FieldMask {
        hiddenFieldIds = hiddenFieldIds == null ? List.of() : List.copyOf(hiddenFieldIds);
        readonlyFieldIds = readonlyFieldIds == null ? List.of() : List.copyOf(readonlyFieldIds);
        requiredFieldIds = requiredFieldIds == null ? List.of() : List.copyOf(requiredFieldIds);
        maskRules = maskRules == null ? List.of() : List.copyOf(maskRules);
    }

    public static FieldMask empty() {
        return new FieldMask(List.of(), List.of(), List.of(), List.of());
    }

    public boolean hidden(String fieldId) {
        return hiddenFieldIds.contains(fieldId);
    }

    public boolean readonly(String fieldId) {
        return !hidden(fieldId) && readonlyFieldIds.contains(fieldId);
    }
}
