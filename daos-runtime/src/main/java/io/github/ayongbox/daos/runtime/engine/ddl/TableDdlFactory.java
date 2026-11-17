package io.github.ayongbox.daos.runtime.engine.ddl;

import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.metadata.common.IndexType;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.table.IndexDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.pipeline.dialect.DdlDialect;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.pipeline.dialect.TypeMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 表物理结构的唯一构建器。建表与结构演进共用，避免两侧列定义漂移。
 */
public final class TableDdlFactory {

    public static final String TENANT_COLUMN = "tenant_id";

    /**
     * 平台保留列：不允许通过结构演进删除、改名或变更类型。
     */
    public static final Set<String> RESERVED_COLUMNS = Set.of("id", TENANT_COLUMN, "version");

    private static final String ID_COLUMN = "id";
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

    private TableDdlFactory() {
    }

    public static DdlDialect.TableDdl table(IrTable table, SqlDialect dialect) {
        return new DdlDialect.TableDdl(table.physicalName(), columns(table, dialect));
    }

    public static List<DdlDialect.ColumnDdl> columns(IrTable table, SqlDialect dialect) {
        List<DdlDialect.ColumnDdl> columns = new ArrayList<>();
        appendAutoColumns(columns, table.source(), dialect);
        appendBusinessColumns(columns, table, dialect);
        appendExtColumn(columns, table, dialect);
        return columns;
    }

    public static boolean isAutoColumn(String physicalName) {
        return AUTO_COLUMNS.contains(physicalName);
    }

    public static boolean isReserved(String physicalName) {
        return RESERVED_COLUMNS.contains(physicalName);
    }

    public static List<DdlDialect.IndexDdl> indexes(IrTable table) {
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

    private static void appendAutoColumns(
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

    private static void appendBusinessColumns(
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

    private static void appendExtColumn(
            List<DdlDialect.ColumnDdl> columns, IrTable table, SqlDialect dialect) {

        if (table.source().storageMode() == StorageMode.PHYSICAL) {
            return;
        }
        columns.add(new DdlDialect.ColumnDdl(
                EXT_COLUMN, dialect.types().autoColumnType(TypeMapper.AutoColumn.EXT),
                true, null, false));
    }

    private static String defaultValue(SchemaField field, SqlDialect dialect) {
        FieldConstraints constraints = field.constraints();
        String expression = constraints.defaultValueExpression();
        if (expression != null && !expression.isBlank()) {
            return expression;
        }
        Object value = constraints.defaultValue();
        return value == null ? null : dialect.ddl().literal(value);
    }
}
