package io.github.ayongbox.daos.runtime.engine.ddl;

import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.MigrationPlan;
import io.github.ayongbox.daos.core.ir.MigrationPlan.MigrationStep;
import io.github.ayongbox.daos.core.ir.MigrationPlan.Operation;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.pipeline.ddl.MigrationCompiler;
import io.github.ayongbox.daos.core.pipeline.dialect.DdlDialect;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 结构演进编译器唯一实现。
 *
 * <p>差异范围为：新增表、列与索引的增删改，以及同一字段 id 的列改名。
 * 不支持删除表；{@code id} / {@code tenant_id} / {@code version} 为保留列，
 * 禁止删除、改名或变更类型。</p>
 */
public final class DefaultMigrationCompiler implements MigrationCompiler {

    @Override
    public MigrationPlan plan(NormalizedSchema from, NormalizedSchema to, SqlDialect dialect) {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(dialect, "dialect");

        List<MigrationStep> steps = new ArrayList<>();
        Map<String, IrTable> oldTables = from == null ? Map.of() : from.tablesByCode();

        for (IrTable target : to.tablesByCode().values()) {
            if (target.source().externalRef() != null) {
                continue;
            }
            IrTable source = oldTables.get(target.source().code());
            if (source == null) {
                addCreateTable(steps, target, dialect);
            } else if (source.source().externalRef() != null) {
                throw new IllegalArgumentException(
                        "外部引用表不能演进为本地表: " + target.source().code());
            } else {
                diffTable(steps, source, target, dialect);
            }
        }

        for (Map.Entry<String, IrTable> entry : oldTables.entrySet()) {
            if (!to.tablesByCode().containsKey(entry.getKey())
                    && entry.getValue().source().externalRef() == null) {
                throw new IllegalArgumentException(
                        "暂不支持删除表: " + entry.getKey() + "，请先归档数据后手工处理");
            }
        }

        return new MigrationPlan(
                to.appId(),
                from == null ? null : from.snapshotId(),
                from == null ? 0 : from.releaseVersion(),
                null,
                to.releaseVersion(),
                steps);
    }

    private void addCreateTable(List<MigrationStep> steps, IrTable table, SqlDialect dialect) {
        String physical = table.physicalName();
        steps.add(new MigrationStep(
                Operation.CREATE_TABLE,
                table.source().code(),
                physical,
                dialect.ddl().createTable(TableDdlFactory.table(table, dialect)).sql(),
                dialect.ddl().dropTable(physical).sql()));

        for (DdlDialect.IndexDdl index : TableDdlFactory.indexes(table)) {
            addCreateIndex(steps, table, index, dialect);
        }
    }

    private void diffTable(
            List<MigrationStep> steps, IrTable source, IrTable target, SqlDialect dialect) {

        String physical = target.physicalName();
        Map<String, DdlDialect.ColumnDdl> oldColumns = byName(TableDdlFactory.columns(source, dialect));
        Map<String, DdlDialect.ColumnDdl> newColumns = byName(TableDdlFactory.columns(target, dialect));

        Map<String, DdlDialect.IndexDdl> oldIndexes = indexesByName(source, dialect);
        Map<String, DdlDialect.IndexDdl> newIndexes = indexesByName(target, dialect);

        for (Map.Entry<String, DdlDialect.IndexDdl> entry : oldIndexes.entrySet()) {
            DdlDialect.IndexDdl replacement = newIndexes.get(entry.getKey());
            if (replacement == null || !sameIndex(entry.getValue(), replacement)) {
                steps.add(new MigrationStep(
                        Operation.DROP_INDEX,
                        target.source().code(),
                        entry.getKey(),
                        dialect.ddl().dropIndex(physical, entry.getKey()).sql(),
                        dialect.ddl().createIndex(physical, entry.getValue()).sql()));
            }
        }

        Map<String, String> oldNameByFieldId = new HashMap<>();
        for (IrColumn column : source.physicalColumns()) {
            if (!TableDdlFactory.isAutoColumn(column.physicalName())) {
                oldNameByFieldId.put(column.fieldId(), column.physicalName());
            }
        }

        Map<String, DdlDialect.ColumnDdl> effectiveOld = new LinkedHashMap<>(oldColumns);
        Set<String> handledNames = new HashSet<>();
        for (IrColumn column : target.physicalColumns()) {
            if (TableDdlFactory.isAutoColumn(column.physicalName())) {
                continue;
            }
            String oldName = oldNameByFieldId.get(column.fieldId());
            if (oldName == null || oldName.equals(column.physicalName())) {
                continue;
            }
            requireNotReserved(oldName);
            requireNotReserved(column.physicalName());
            DdlDialect.ColumnDdl definition = effectiveOld.remove(oldName);
            if (definition == null) {
                throw new IllegalStateException("列改名源不存在: " + oldName);
            }
            effectiveOld.put(column.physicalName(), rename(definition, column.physicalName()));
            handledNames.add(column.physicalName());
            steps.add(new MigrationStep(
                    Operation.RENAME_COLUMN,
                    target.source().code(),
                    column.physicalName(),
                    dialect.ddl().renameColumn(physical, oldName, column.physicalName()).sql(),
                    dialect.ddl().renameColumn(physical, column.physicalName(), oldName).sql()));
        }

        for (Map.Entry<String, DdlDialect.ColumnDdl> entry : newColumns.entrySet()) {
            String name = entry.getKey();
            if (effectiveOld.containsKey(name) || handledNames.contains(name)) {
                continue;
            }
            requireNotReserved(name);
            DdlDialect.ColumnDdl column = entry.getValue();
            if (!column.nullable() && column.defaultValue() == null && !column.primaryKey()) {
                throw new IllegalArgumentException(
                        "新增必填列必须提供默认值: " + target.source().code() + "." + name);
            }
            steps.add(new MigrationStep(
                    Operation.ADD_COLUMN,
                    target.source().code(),
                    name,
                    dialect.ddl().addColumn(physical, column).sql(),
                    dialect.ddl().dropColumn(physical, name).sql()));
        }

        for (Map.Entry<String, DdlDialect.ColumnDdl> entry : effectiveOld.entrySet()) {
            String name = entry.getKey();
            if (newColumns.containsKey(name) || handledNames.contains(name)) {
                continue;
            }
            requireNotReserved(name);
            steps.add(new MigrationStep(
                    Operation.DROP_COLUMN,
                    target.source().code(),
                    name,
                    dialect.ddl().dropColumn(physical, name).sql(),
                    dialect.ddl().addColumn(physical, entry.getValue()).sql()));
        }

        for (Map.Entry<String, DdlDialect.ColumnDdl> entry : newColumns.entrySet()) {
            String name = entry.getKey();
            DdlDialect.ColumnDdl oldColumn = effectiveOld.get(name);
            if (oldColumn == null) {
                continue;
            }
            DdlDialect.ColumnDdl newColumn = entry.getValue();
            if (oldColumn.primaryKey() != newColumn.primaryKey()) {
                throw new IllegalArgumentException("主键定义不支持通过结构演进变更: " + name);
            }
            if (sameColumn(oldColumn, newColumn)) {
                continue;
            }
            requireNotReserved(name);
            steps.add(new MigrationStep(
                    Operation.ALTER_COLUMN,
                    target.source().code(),
                    name,
                    dialect.ddl().alterColumn(physical, oldColumn, newColumn).sql(),
                    dialect.ddl().alterColumn(physical, newColumn, oldColumn).sql()));
        }

        for (Map.Entry<String, DdlDialect.IndexDdl> entry : newIndexes.entrySet()) {
            DdlDialect.IndexDdl existing = oldIndexes.get(entry.getKey());
            if (existing == null || !sameIndex(existing, entry.getValue())) {
                addCreateIndex(steps, target, entry.getValue(), dialect);
            }
        }
    }

    private void addCreateIndex(
            List<MigrationStep> steps, IrTable table, DdlDialect.IndexDdl index, SqlDialect dialect) {
        String physical = table.physicalName();
        String name = dialect.ddl().indexName(physical, index);
        steps.add(new MigrationStep(
                Operation.CREATE_INDEX,
                table.source().code(),
                name,
                dialect.ddl().createIndex(physical, index).sql(),
                dialect.ddl().dropIndex(physical, name).sql()));
    }

    private static Map<String, DdlDialect.ColumnDdl> byName(List<DdlDialect.ColumnDdl> columns) {
        Map<String, DdlDialect.ColumnDdl> result = new LinkedHashMap<>();
        for (DdlDialect.ColumnDdl column : columns) {
            result.put(column.name(), column);
        }
        return result;
    }

    private static Map<String, DdlDialect.IndexDdl> indexesByName(IrTable table, SqlDialect dialect) {
        Map<String, DdlDialect.IndexDdl> result = new LinkedHashMap<>();
        for (DdlDialect.IndexDdl index : TableDdlFactory.indexes(table)) {
            result.put(dialect.ddl().indexName(table.physicalName(), index), index);
        }
        return result;
    }

    private static boolean sameColumn(DdlDialect.ColumnDdl left, DdlDialect.ColumnDdl right) {
        return left.sqlType().equals(right.sqlType())
                && left.nullable() == right.nullable()
                && Objects.equals(left.defaultValue(), right.defaultValue());
    }

    private static boolean sameIndex(DdlDialect.IndexDdl left, DdlDialect.IndexDdl right) {
        return left.columns().equals(right.columns())
                && left.unique() == right.unique()
                && left.type() == right.type()
                && Objects.equals(left.where(), right.where());
    }

    private static DdlDialect.ColumnDdl rename(DdlDialect.ColumnDdl column, String newName) {
        return new DdlDialect.ColumnDdl(
                newName, column.sqlType(), column.nullable(), column.defaultValue(), column.primaryKey());
    }

    private static void requireNotReserved(String columnName) {
        if (TableDdlFactory.isReserved(columnName)) {
            throw new IllegalArgumentException(
                    "保留列不允许删除、改名或变更类型: " + columnName);
        }
    }
}
