package io.github.ayongbox.daos.core.metadata.view;

import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;

import java.util.Objects;

/**
 * 汇总视图的 HAVING 条件。{@code aggregation} 引用汇总列名称，在分组与汇总之后生效。
 */
public record ViewHaving(
        String aggregation,
        ConditionOperator operator,
        Object value) {

    public ViewHaving {
        Objects.requireNonNull(aggregation, "aggregation");
        Objects.requireNonNull(operator, "operator");
        switch (operator) {
            case EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN -> {
            }
            default -> throw new IllegalArgumentException(
                    "HAVING 不支持运算符: " + operator.code());
        }
        if (value == null) {
            throw new IllegalArgumentException("HAVING 条件值不能为空: " + aggregation);
        }
    }
}
