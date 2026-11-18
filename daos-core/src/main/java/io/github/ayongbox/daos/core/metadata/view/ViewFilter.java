package io.github.ayongbox.daos.core.metadata.view;

import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 视图默认筛选条件。运行时与用户临时筛选按 AND 合并。
 *
 * <p>值只接受字面量；涉及当前用户、部门等上下文变量的运算符由权限链路处理，
 * 视图筛选不允许使用。</p>
 */
public record ViewFilter(
        String fieldId,
        ConditionOperator operator,
        Object value) {

    public ViewFilter {
        Objects.requireNonNull(fieldId, "fieldId");
        operator = operator == null ? ConditionOperator.EQ : operator;
        if (!ALLOWED_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException(
                    "视图筛选不支持运算符: " + operator.code() + "，上下文变量请使用权限条件");
        }
    }

    private static final Set<ConditionOperator> ALLOWED_OPERATORS = EnumSet.of(
            ConditionOperator.EQ,
            ConditionOperator.NE,
            ConditionOperator.CONTAINS,
            ConditionOperator.NOT_CONTAINS,
            ConditionOperator.STARTS_WITH,
            ConditionOperator.ENDS_WITH,
            ConditionOperator.EMPTY,
            ConditionOperator.NOT_EMPTY,
            ConditionOperator.GT,
            ConditionOperator.GTE,
            ConditionOperator.LT,
            ConditionOperator.LTE,
            ConditionOperator.BETWEEN,
            ConditionOperator.IN,
            ConditionOperator.NOT_IN);
}
