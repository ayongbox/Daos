package io.github.ayongbox.daos.core.pipeline.ddl;

import io.github.ayongbox.daos.core.ir.MigrationPlan;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;

/**
 * 结构演进编译器。输入前后两个归一化 Schema，输出可执行、可回滚的差异计划。
 *
 * <p>{@code from} 为 null 表示应用首次发布，此时计划只包含建表与建索引。</p>
 */
public interface MigrationCompiler {

    MigrationPlan plan(NormalizedSchema from, NormalizedSchema to, SqlDialect dialect);
}
