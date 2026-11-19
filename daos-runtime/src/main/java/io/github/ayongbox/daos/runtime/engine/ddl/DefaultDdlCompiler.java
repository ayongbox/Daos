package io.github.ayongbox.daos.runtime.engine.ddl;

import io.github.ayongbox.daos.core.ir.DdlPlan;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.pipeline.ddl.DdlCompiler;
import io.github.ayongbox.daos.core.pipeline.dialect.DdlDialect;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DDL 编译器唯一实现。方言无关：类型、引号、索引语法全部委托给 {@link SqlDialect}。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>外部只读投影（{@code externalRef != null}）不建表；</li>
 *   <li>派生列与 JSON 成员不建列，提升列建普通列；</li>
 *   <li>自动列由平台注入，字段模型中的同名系统字段不重复建列；</li>
 *   <li>唯一索引以 {@code tenant_id} 为前导列，避免跨租户冲突。</li>
 * </ul>
 */
public final class DefaultDdlCompiler implements DdlCompiler {

    @Override
    public DdlPlan compile(NormalizedSchema schema, SqlDialect dialect) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(dialect, "dialect");

        List<DdlPlan.Statement> statements = new ArrayList<>();
        for (IrTable table : schema.tablesById().values()) {
            statements.addAll(compileTable(schema, table, dialect));
        }
        return new DdlPlan(statements);
    }

    private List<DdlPlan.Statement> compileTable(
            NormalizedSchema schema, IrTable table, SqlDialect dialect) {
        if (table.source().externalRef() != null) {
            return List.of();
        }

        DdlDialect.TableDdl tableDdl = TableDdlFactory.table(schema, table, dialect);
        List<DdlPlan.Statement> statements = new ArrayList<>();
        statements.add(new DdlPlan.Statement(
                DdlPlan.Operation.CREATE_TABLE,
                table.physicalName(),
                dialect.ddl().createTable(tableDdl).sql()));

        for (DdlDialect.IndexDdl index : TableDdlFactory.indexes(table)) {
            statements.add(new DdlPlan.Statement(
                    DdlPlan.Operation.CREATE_INDEX,
                    dialect.ddl().indexName(table.physicalName(), index),
                    dialect.ddl().createIndex(table.physicalName(), index).sql()));
        }
        return statements;
    }
}
