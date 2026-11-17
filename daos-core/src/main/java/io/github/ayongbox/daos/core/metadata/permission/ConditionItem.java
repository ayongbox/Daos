package io.github.ayongbox.daos.core.metadata.permission;

import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.ValueType;

import java.util.Objects;

/**
 * 行级条件项。支持静态值、引用字段与上下文变量。
 */
public record ConditionItem(
        String fieldCode,
        ConditionOperator operator,
        ValueType valueType,
        Object value,
        String valueFieldCode,
        String valueExpression) {

    public ConditionItem {
        Objects.requireNonNull(fieldCode, "fieldCode");
        Objects.requireNonNull(operator, "operator");
        valueType = valueType == null ? ValueType.ANY : valueType;
    }
}
