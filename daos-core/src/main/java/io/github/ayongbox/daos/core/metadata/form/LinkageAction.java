package io.github.ayongbox.daos.core.metadata.form;

import java.util.List;
import java.util.Objects;

/**
 * 联动动作。字段级动作使用 fieldId，多字段动作使用 targetFieldIds。
 */
public record LinkageAction(
        String fieldId,
        List<String> targetFieldIds,
        LinkageActionType action,
        Object value,
        String expression) {

    public LinkageAction {
        Objects.requireNonNull(action, "action");
        targetFieldIds = targetFieldIds == null ? List.of() : List.copyOf(targetFieldIds);
        if (fieldId == null && targetFieldIds.isEmpty()) {
            throw new IllegalArgumentException("联动动作 " + action + " 必须指定目标字段");
        }
    }
}
