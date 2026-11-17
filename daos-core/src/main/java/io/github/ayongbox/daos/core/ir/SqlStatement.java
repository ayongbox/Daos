package io.github.ayongbox.daos.core.ir;

import java.util.List;
import java.util.Objects;

/**
 * 方言渲染产物。参数一律走占位符，禁止拼接字面量。
 */
public record SqlStatement(String sql, List<Object> parameters) {

    public SqlStatement {
        Objects.requireNonNull(sql, "sql");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    public static SqlStatement of(String sql, Object... parameters) {
        return new SqlStatement(sql, List.of(parameters));
    }
}
