package io.github.ayongbox.daos.core.metadata.view;

import io.github.ayongbox.daos.core.ir.AggregateFunction;

import java.util.Objects;

/**
 * 视图汇总列。COUNT 可以不带字段（ COUNT(*) ），其余函数必须引用表字段。
 */
public record ViewAggregation(
        AggregateFunction function,
        String fieldId,
        String displayName) {

    public ViewAggregation {
        Objects.requireNonNull(function, "function");
        if (function != AggregateFunction.COUNT && (fieldId == null || fieldId.isBlank())) {
            throw new IllegalArgumentException(function.code() + " 必须指定字段");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("汇总列名称不能为空");
        }
        if (displayName.length() > 32) {
            throw new IllegalArgumentException("汇总列名称不能超过 32 个字符: " + displayName);
        }
    }
}
