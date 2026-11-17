package io.github.ayongbox.daos.core.metadata.form;

import io.github.ayongbox.daos.core.metadata.common.FormScene;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 校验规则。前端提示与服务端重校验共用，服务端为唯一裁决方。
 */
public record ValidationRule(
        String id,
        String name,
        List<String> targetFieldIds,
        ValidationType type,
        Map<String, Object> params,
        String message,
        ValidationLevel level,
        ValidationTrigger trigger,
        List<FormScene> scenes,
        boolean enabled) {

    public ValidationRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (targetFieldIds == null || targetFieldIds.isEmpty()) {
            throw new IllegalArgumentException("校验规则 " + id + " 必须指定作用字段");
        }
        targetFieldIds = List.copyOf(targetFieldIds);
        params = params == null ? Map.of() : Map.copyOf(params);
        level = level == null ? ValidationLevel.ERROR : level;
        trigger = trigger == null ? ValidationTrigger.SUBMIT : trigger;
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
    }
}
