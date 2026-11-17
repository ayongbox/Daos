package io.github.ayongbox.daos.runtime.engine.dialect;

import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.common.IndexType;
import io.github.ayongbox.daos.core.pipeline.Dialect;
import io.github.ayongbox.daos.core.pipeline.dialect.DdlDialect;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * PostgreSQL 15 方言。索引名在 schema 内全局唯一，统一加物理表名前缀。
 *
 * <p>查询侧使用 ILIKE，与 MySQL 默认大小写不敏感的排序规则保持语义一致。</p>
 */
public final class PostgresDialect extends AbstractSqlDialect {

    private static final int MAX_IDENTIFIER_LENGTH = 63;

    public PostgresDialect() {
        super(new Types(), new Ddl(), "ILIKE");
    }

    @Override
    public Dialect type() {
        return Dialect.POSTGRESQL;
    }

    @Override
    protected char quoteChar() {
        return '"';
    }

    private static final class Types extends AbstractTypeMapper {

        @Override
        protected String jsonType() {
            return "jsonb";
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
            return DialectSupport.truncate(
                    physicalTable + "_" + index.name(), MAX_IDENTIFIER_LENGTH);
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
            if (index.type() == IndexType.FULLTEXT) {
                throw new IllegalArgumentException(
                        "PostgreSQL 不支持 FULLTEXT 索引，请改用 GIN: " + index.name());
            }
            StringBuilder sql = new StringBuilder(index.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ")
                    .append(quote(indexName(physicalTable, index)))
                    .append(" ON ").append(quote(physicalTable))
                    .append(" USING ").append(index.type().name().toLowerCase(Locale.ROOT))
                    .append(" (").append(joinColumns(index.columns())).append(')');
            if (index.where() != null) {
                sql.append(" WHERE ").append(index.where());
            }
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

        private String joinColumns(List<String> columns) {
            StringJoiner joiner = new StringJoiner(", ");
            columns.forEach(column -> joiner.add(quote(column)));
            return joiner.toString();
        }
    }
}
