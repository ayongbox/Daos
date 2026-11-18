package io.github.ayongbox.daos.core.ir;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 聚合函数。COUNT 不要求字段，其余函数必须引用具体列。
 */
public enum AggregateFunction {

    COUNT("count"),
    COUNT_DISTINCT("count_distinct"),
    SUM("sum"),
    AVG("avg"),
    MIN("min"),
    MAX("max");

    private final String code;

    AggregateFunction(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public boolean requiresNumericField() {
        return this == SUM || this == AVG;
    }

    @JsonCreator
    public static AggregateFunction fromCode(String code) {
        for (AggregateFunction function : values()) {
            if (function.code.equals(code)) {
                return function;
            }
        }
        throw new IllegalArgumentException("未知聚合函数: " + code);
    }
}
