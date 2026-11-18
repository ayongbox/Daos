package io.github.ayongbox.daos.runtime.engine.dialect;

import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.pipeline.Dialect;
import io.github.ayongbox.daos.core.pipeline.dialect.DdlDialect;

import java.util.List;
import java.util.StringJoiner;

/**
 * H2 方言。仅用于本地开发与测试，生产目标库仍是 PostgreSQL / MySQL。
 *
 * <p>与 PostgreSQL 的差异：H2 没有 {@code jsonb}，JSON 统一用宽 varchar 存储以保证字符串
 * 参数可直接写入与读出；索引不支持 {@code USING} 与部分索引；修改列类型使用
 * {@code SET DATA TYPE} 语法。</p>
 */
public final class H2Dialect extends AbstractSqlDialect {

    public H2Dialect() {
        super(new Types(), new Ddl(), "LIKE");
    }

    @Override
    public Dialect type() {
        return Dialect.H2;
    }

    @Override
    protected char quoteChar() {
        return '"';
    }

    private static final class Types extends AbstractTypeMapper {

        @Override
        protected String jsonType() {
            return "varchar(1000000)";
        }

        @Override
        protected String timestampType(int precision) {
            return "timestamp(" + precision + ")";
        }

        @Override
        protected String booleanType() {
            return "boolean";
        }

        @Override
        protected String smallIntType() {
            return "smallint";
        }

        @Override
        protected String decimalType(int precision, int scale) {
            return "numeric(" + precision + "," + scale + ")";
        }
    }

    private static final class Ddl implements DdlDialect {

        @Override
        public String quote(String identifier) {
            return DialectSupport.quote(identifier, '"');
        }

        @Override
        public String literal(Object value) {
            return DialectSupport.literal(value, true);
        }

        @Override
        public String indexName(String physicalTable, IndexDdl index) {
            return physicalTable + "_" + index.name();
        }

        @Override
        public SqlStatement createTable(TableDdl table) {
            StringBuilder sql = new StringBuilder("CREATE TABLE ")
                    .append(quote(table.physicalName())).append(" (\n");
            List<ColumnDdl> columns = table.columns();
            for (int index = 0; index < columns.size(); index++) {
                ColumnDdl column = columns.get(index);
                sql.append("  ").append(quote(column.name())).append(' ').append(column.sqlType());
                if (!column.nullable()) {
                    sql.append(" NOT NULL");
                }
                if (column.defaultValue() != null) {
                    sql.append(" DEFAULT ").append(column.defaultValue());
                }
                if (column.primaryKey()) {
                    sql.append(" PRIMARY KEY");
                }
                if (index < columns.size() - 1) {
                    sql.append(',');
                }
                sql.append('\n');
            }
            sql.append(')');
            return SqlStatement.of(sql.toString());
        }

        @Override
        public SqlStatement createIndex(String physicalTable, IndexDdl index) {
            // H2 不支持 USING 与部分索引，忽略 where 以保证本地可发布。
            StringBuilder sql = new StringBuilder(index.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
                    .append(quote(indexName(physicalTable, index)))
                    .append(" ON ").append(quote(physicalTable))
                    .append(" (").append(joinColumns(index.columns())).append(')');
            return SqlStatement.of(sql.toString());
        }

        @Override
        public SqlStatement dropTable(String physicalTable) {
            return SqlStatement.of("DROP TABLE IF EXISTS " + quote(physicalTable));
        }

        @Override
        public SqlStatement dropIndex(String physicalTable, String indexName) {
            return SqlStatement.of("DROP INDEX IF EXISTS " + quote(indexName));
        }

        @Override
        public SqlStatement addColumn(String physicalTable, ColumnDdl column) {
            return SqlStatement.of("ALTER TABLE " + quote(physicalTable)
                    + " ADD COLUMN " + columnDefinition(column));
        }

        @Override
        public SqlStatement dropColumn(String physicalTable, String columnName) {
            return SqlStatement.of("ALTER TABLE " + quote(physicalTable)
                    + " DROP COLUMN " + quote(columnName));
        }

        @Override
        public SqlStatement alterColumn(String physicalTable, ColumnDdl oldColumn, ColumnDdl newColumn) {
            String column = quote(newColumn.name());
            java.util.List<String> actions = new java.util.ArrayList<>();
            if (!oldColumn.sqlType().equals(newColumn.sqlType())) {
                actions.add("ALTER COLUMN " + column + " SET DATA TYPE " + newColumn.sqlType());
            }
            if (oldColumn.nullable() != newColumn.nullable()) {
                actions.add("ALTER COLUMN " + column
                        + (newColumn.nullable() ? " DROP NOT NULL" : " SET NOT NULL"));
            }
            if (!java.util.Objects.equals(oldColumn.defaultValue(), newColumn.defaultValue())) {
                actions.add(newColumn.defaultValue() == null
                        ? "ALTER COLUMN " + column + " DROP DEFAULT"
                        : "ALTER COLUMN " + column + " SET DEFAULT " + newColumn.defaultValue());
            }
            if (actions.isEmpty()) {
                throw new IllegalArgumentException("列定义没有变化: " + newColumn.name());
            }
            return SqlStatement.of("ALTER TABLE " + quote(physicalTable)
                    + " " + String.join(", ", actions));
        }

        @Override
        public SqlStatement renameColumn(String physicalTable, String from, String to) {
            return SqlStatement.of("ALTER TABLE " + quote(physicalTable)
                    + " RENAME COLUMN " + quote(from) + " TO " + quote(to));
        }

        private String columnDefinition(ColumnDdl column) {
            StringBuilder sql = new StringBuilder(quote(column.name()))
                    .append(' ').append(column.sqlType());
            if (!column.nullable()) {
                sql.append(" NOT NULL");
            }
            if (column.defaultValue() != null) {
                sql.append(" DEFAULT ").append(column.defaultValue());
            }
            return sql.toString();
        }

        private String joinColumns(List<String> columns) {
            StringJoiner joiner = new StringJoiner(", ");
            columns.forEach(column -> joiner.add(quote(column)));
            return joiner.toString();
        }
    }
}
