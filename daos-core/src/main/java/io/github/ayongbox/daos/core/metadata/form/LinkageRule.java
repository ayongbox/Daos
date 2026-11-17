package io.github.ayongbox.daos.core.metadata.form;

import io.github.ayongbox.daos.core.metadata.common.FormScene;
import io.github.ayongbox.daos.core.metadata.common.LogicOperator;

import java.util.List;
import java.util.Objects;

/**
 * 联动规则：满足 when 执行 then，否则执行 otherwise。
 */
public record LinkageRule(
        String id,
        String name,
        int priority,
        boolean enabled,
        List<FormScene> scenes,
        LogicOperator logic,
        List<LinkageCondition> when,
        List<LinkageAction> then,
        List<LinkageAction> otherwise) {

    public LinkageRule {
        Objects.requireNonNull(id, "id");
        if (when == null || when.isEmpty()) {
            throw new IllegalArgumentException("联动规则 " + id + " 至少需要一个触发条件");
        }
        when = List.copyOf(when);
        then = then == null ? List.of() : List.copyOf(then);
        otherwise = otherwise == null ? List.of() : List.copyOf(otherwise);
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
        logic = logic == null ? LogicOperator.AND : logic;
        if (priority == 0) {
            priority = 100;
        }
    }
}
