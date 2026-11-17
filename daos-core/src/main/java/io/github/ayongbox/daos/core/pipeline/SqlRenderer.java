package io.github.ayongbox.daos.core.pipeline;

import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.SqlStatement;

/**
 * 方言渲染器。只做 AST 到 SQL 的翻译，不做权限与业务判断。
 */
public interface SqlRenderer {

    Dialect dialect();

    SqlStatement render(QueryPlan plan);
}
