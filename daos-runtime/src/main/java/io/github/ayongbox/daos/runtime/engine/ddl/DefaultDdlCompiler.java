package io.github.ayongbox.daos.runtime.engine.ddl;

import io.github.ayongbox.daos.core.ir.DdlPlan;
import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.metadata.common.IndexType;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.table.IndexDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.pipeline.ddl.DdlCompiler;
import io.github.ayongbox.daos.core.pipeline.dialect.DdlDialect;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.pipeline.dialect.TypeMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    private static final String ID_COLUMN = "id";
    private static final String TENANT_COLUMN = "tenant_id";
    private static final String CREATED_BY_COLUMN = "created_by";
    private static final String CREATED_AT_COLUMN = "created_at";
    private static final String UPDATED_BY_COLUMN = "updated_by";
    private static final String UPDATED_AT_COLUMN = "updated_at";
    private static final String VERSION_COLUMN = "version";
    private static final String DELETED_COLUMN = "deleted";
    private static final String EXT_COLUMN = "_ext";

    private static final Set<String> AUTO_COLUMNS = Set.of(
            ID_COLUMN, TENANT_COLUMN, CREATED_BY_COLUMN, CREATED_AT_COLUMN,
            UPDATED_BY_COLUMN, UPDATED_AT_COLUMN, VERSION_COLUMN, DELETED_COLUMN, EXT_COLUMN);

    @Override
    public DdlPlan compile(NormalizedSchema schema, SqlDialect dialect) {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(dialect, "dialect");

        List<DdlPlan.Statement> statements = new ArrayList<>();
        for (IrTable table : schema.tablesById().values()) {
            statements.addAll(compileTable(table, dialect));
        }
        return new DdlPlan(statements);
    }

    private List<DdlPlan.Statement> compileTable(IrTable table, SqlDialect dialect) {
        SchemaTable source = table.source();
        if (source.externalRef() != null) {
            return List.of();
        }

        List<DdlDialect.ColumnDdl> columns = new ArrayList<>();
        appendAutoColumns(columns, source, dialect);
        appendBusinessColumns(columns, table, dialect);
        appendExtColumn(columns, source, dialect);

        List<DdlPlan.Statement> statements = new ArrayList<>();
        DdlDialect.TableDdl tableDdl = new DdlDialect.TableDdl(table.physicalName(), columns);
        statements.add(new DdlPlan.Statement(
                DdlPlan.Operation.CREATE_TABLE,
                table.physicalName(),
                dialect.ddl().createTable(tableDdl).sql()));

        for (DdlDialect.IndexDdl index : indexesOf(table)) {
            statements.add(new DdlPlan.Statement(
                    DdlPlan.Operation.CREATE_INDEX,
                    dialect.ddl().indexName(table.physicalName(), index),
                    dialect.ddl().createIndex(table.physicalName(), index).sql()));
        }
        return statements;
    }

    private void appendAutoColumns(
            List<DdlDialect.ColumnDdl> columns, SchemaTable table, SqlDialect dialect) {

        TypeMapper types = dialect.types();
        DdlDialect ddl = dialect.ddl();

        columns.add(new DdlDialect.ColumnDdl(
                ID_COLUMN, types.autoColumnType(TypeMapper.AutoColumn.ID), false, null, true));
        if (table.options().tenantScoped()) {
            columns.add(new DdlDialect.ColumnDdl(
                    TENANT_COLUMN, types.autoColumnType(TypeMapper.AutoColumn.TENANT_ID),
                    false, null, false));
        }
        if (table.options().auditEnabled()) {
            columns.add(new DdlDialect.ColumnDdl(
                    CREATED_BY_COLUMN, types.autoColumnType(TypeMapper.AutoColumn.CREATED_BY),
                    true, null, false));
            columns.add(new DdlDialect.ColumnDdl(
                    CREATED_AT_COLUMN, types.autoColumnType(TypeMapper.AutoColumn.CREATED_AT),
                    true, null, false));
            columns.add(new DdlDialect.ColumnDdl(
                    UPDATED_BY_COLUMN, types.autoColumnType(TypeMapper.AutoColumn.UPDATED_BY),
                    true, null, false));
            columns.add(new DdlDialect.ColumnDdl(
                    UPDATED_AT_COLUMN, types.autoColumnType(TypeMapper.AutoColumn.UPDATED_AT),
                    true, null, false));
        }
        columns.add(new DdlDialect.ColumnDdl(
                VERSION_COLUMN, types.autoColumnType(TypeMapper.AutoColumn.VERSION),
                false, "0", false));
        if (table.options().softDelete()) {
            columns.add(new DdlDialect.ColumnDdl(
                    DELETED_COLUMN, types.autoColumnType(TypeMapper.AutoColumn.DELETED),
                    false, ddl.literal(false), false));
        }
    }

    private void appendBusinessColumns(
            List<DdlDialect.ColumnDdl> columns, IrTable table, SqlDialect dialect) {

        for (IrColumn column : table.columns()) {
            if (!column.hasPhysicalColumn()) {
                continue;
            }
            String physicalName = column.physicalName();
            if (AUTO_COLUMNS.contains(physicalName)) {
                continue;
            }
            SchemaField field = column.source();
            FieldConstraints constraints = field.constraints();
            columns.add(new DdlDialect.ColumnDdl(
                    physicalName,
                    dialect.types().sqlType(field),
                    !constraints.required(),
                    defaultValue(field, dialect),
                    false));
        }
    }

    private void appendExtColumn(
            List<DdlDialect.ColumnDdl> columns, SchemaTable table, SqlDialect dialect) {

        if (table.storageMode() == StorageMode.PHYSICAL) {
            return;
        }
        columns.add(new DdlDialect.ColumnDdl(
                EXT_COLUMN, dialect.types().autoColumnType(TypeMapper.AutoColumn.EXT),
                true, null, false));
    }

    private String defaultValue(SchemaField field, SqlDialect dialect) {
        FieldConstraints constraints = field.constraints();
        String expression = constraints.defaultValueExpression();
        if (expression != null && !expression.isBlank()) {
            return expression;
        }
        Object value = constraints.defaultValue();
        return value == null ? null : dialect.ddl().literal(value);
    }

    private List<DdlDialect.IndexDdl> indexesOf(IrTable table) {
        boolean tenantScoped = table.source().options().tenantScoped();
        List<DdlDialect.IndexDdl> indexes = new ArrayList<>();

        for (IrColumn column : table.physicalColumns()) {
            FieldConstraints constraints = column.source().constraints();
            if (!constraints.unique() && !constraints.indexed()) {
                continue;
            }
            List<String> indexColumns = new ArrayList<>();
            if (constraints.unique() && tenantScoped) {
                indexColumns.add(TENANT_COLUMN);
            }
            indexColumns.add(column.physicalName());
            indexes.add(new DdlDialect.IndexDdl(
                    (constraints.unique() ? "uk_" : "idx_") + column.physicalName(),
                    indexColumns, constraints.unique(), IndexType.BTREE, null));
        }

        for (IndexDef definition : table.source().indexes()) {
            List<String> indexColumns = new ArrayList<>();
            for (String fieldCode : definition.columns()) {
                IrColumn column = table.requireColumn(fieldCode);
                if (!column.hasPhysicalColumn()) {
                    throw new IllegalArgumentException(
                            "索引 " + definition.name() + " 引用了非物理列: " + fieldCode);
                }
                indexColumns.add(column.physicalName());
            }
            indexes.add(new DdlDialect.IndexDdl(
                    definition.name(), indexColumns, definition.unique(),
                    definition.type(), definition.where()));
        }
        return indexes;
    }
}
