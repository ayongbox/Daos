package io.github.ayongbox.daos.core.pipeline.ddl;

import io.github.ayongbox.daos.core.ir.DdlPlan;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;

/**
 * DDL 编译器。纯函数：同一输入必须产出逐字节一致的语句。
 *
 * <p>只读归一化 IR，物理列名、类型与索引由 IR 与方言共同决定。实现只允许存在一份，
 * 方言差异全部收敛到 {@link SqlDialect}。</p>
 */
public interface DdlCompiler {

    DdlPlan compile(NormalizedSchema schema, SqlDialect dialect);
}
