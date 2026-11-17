package io.github.ayongbox.daos.core.metadata.form;

import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.ValueType;

import java.util.Objects;

/**
 * 联动条件：静态值或引用其他字段的值。
 */
public record LinkageCondition(
        String fieldId,
        ConditionOperator operator,
        Object value,
        String valueFieldId,
        ValueType valueType) {

    public LinkageCondition {
        Objects.requireNonNull(fieldId, "fieldId");
        Objects.requireNonNull(operator, "operator");
        valueType = valueType == null ? ValueType.ANY : valueType;
    }
}
