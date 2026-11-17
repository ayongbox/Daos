package io.github.ayongbox.daos.core.ir;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 方言渲染产物。参数一律走占位符，禁止拼接字面量。
 *
 * <p>参数值允许为 {@code null}（SQL NULL 绑定），因此不能使用 {@link List#copyOf} 拷贝。</p>
 */
public record SqlStatement(String sql, List<Object> parameters) {

    public SqlStatement {
        Objects.requireNonNull(sql, "sql");
        parameters = parameters == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    public static SqlStatement of(String sql, Object... parameters) {
        return new SqlStatement(sql, parameters == null ? List.of() : Arrays.asList(parameters));
    }
}
