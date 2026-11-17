package io.github.ayongbox.daos.runtime.engine.dialect;

import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.common.IndexType;
import io.github.ayongbox.daos.core.pipeline.Dialect;
import io.github.ayongbox.daos.core.pipeline.dialect.DdlDialect;

import java.util.List;
import java.util.StringJoiner;

/**
 * MySQL 8.0 方言。索引名为表内作用域，不带表名前缀。
 */
public final class MysqlDialect extends AbstractSqlDialect {

    private static final int MAX_INDEX_NAME_LENGTH = 64;

    public MysqlDialect() {
        super(new Types(), new Ddl(), "LIKE");
    }

    @Override
    public Dialect type() {
        return Dialect.MYSQL;
    }

    @Override
    protected char quoteChar() {
        return '`';
    }

    private static final class Types extends AbstractTypeMapper {

        @Override
        protected String jsonType() {
            return "json";
        }

        @Override
        protected String timestampType(int precision) {
            return "datetime(" + precision + ")";
        }

        @Override
        protected String booleanType() {
            return "tinyint(1)";
        }

        @Override
        protected String smallIntType() {
            return "tinyint";
        }
    }

    private static final class Ddl implements DdlDialect {

        @Override
        public String quote(String identifier) {
            return DialectSupport.quote(identifier, '`');
        }

        @Override
        public String literal(Object value) {
            return DialectSupport.literal(value, false);
        }

        @Override
        public String indexName(String physicalTable, IndexDdl index) {
            return DialectSupport.truncate(index.name(), MAX_INDEX_NAME_LENGTH);
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
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
            return SqlStatement.of(sql.toString());
        }

        @Override
        public SqlStatement createIndex(String physicalTable, IndexDdl index) {
            if (index.type() == IndexType.GIN || index.type() == IndexType.GIST) {
                throw new IllegalArgumentException(
                        "MySQL 不支持索引类型 " + index.type() + ": " + index.name());
            }
            StringBuilder sql = new StringBuilder();
            if (index.unique()) {
                sql.append("CREATE UNIQUE INDEX ");
            } else if (index.type() == IndexType.FULLTEXT) {
                sql.append("CREATE FULLTEXT INDEX ");
            } else {
                sql.append("CREATE INDEX ");
            }
            sql.append(quote(indexName(physicalTable, index)));
            if (index.type() == IndexType.HASH) {
                sql.append(" USING HASH");
            }
            sql.append(" ON ").append(quote(physicalTable)).append(" (")
                    .append(joinColumns(index.columns())).append(')');
            // MySQL 无部分索引，按契约忽略 where，不静默改写语义。
            return SqlStatement.of(sql.toString());
        }

        @Override
        public SqlStatement dropTable(String physicalTable) {
            return SqlStatement.of("DROP TABLE IF EXISTS " + quote(physicalTable));
        }

        @Override
        public SqlStatement dropIndex(String physicalTable, String indexName) {
            return SqlStatement.of(
                    "DROP INDEX " + quote(indexName) + " ON " + quote(physicalTable));
        }

        private String joinColumns(List<String> columns) {
            StringJoiner joiner = new StringJoiner(", ");
            columns.forEach(column -> joiner.add(quote(column)));
            return joiner.toString();
        }
    }
}
