package io.github.ayongbox.daos.core.pipeline.query;

import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.QueryRequest;

/**
 * 查询编译器。元数据与请求编译为方言无关的 AST，
 * 租户、数据范围、行级条件在独立重写阶段以 AND 方式注入。
 */
public interface QueryCompiler {

    QueryPlan compile(
            NormalizedSchema schema,
            String tableId,
            QueryRequest request,
            PermissionContext permissions);
}
