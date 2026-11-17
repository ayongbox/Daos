package io.github.ayongbox.daos.core.pipeline.dialect;

import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.common.IndexType;

import java.util.List;
import java.util.Objects;

/**
 * 结构演进方言。负责把表的物理结构翻译为 CREATE / DROP 语句。
 *
 * <p>不做配额、权限与合并判断，只处理语法。列名与表名由编译器传入，方言不得改名，
 * 索引名是例外：PostgreSQL 的索引名在 schema 内全局唯一，需要由方言加上表名前缀。</p>
 */
public interface DdlDialect {

    String quote(String identifier);

    /**
     * 把元数据默认值渲染为 SQL 字面量。表达式默认值由编译器直接透传，不经过此方法。
     */
    String literal(Object value);

    /**
     * 方言作用域内的最终索引名，编译器据此填写 {@code DdlPlan.Statement.objectName}。
     */
    String indexName(String physicalTable, IndexDdl index);

    SqlStatement createTable(TableDdl table);

    SqlStatement createIndex(String physicalTable, IndexDdl index);

    SqlStatement dropTable(String physicalTable);

    SqlStatement dropIndex(String physicalTable, String indexName);

    SqlStatement addColumn(String physicalTable, ColumnDdl column);

    SqlStatement dropColumn(String physicalTable, String columnName);

    /**
     * 修改列定义。只有类型、可空或默认值发生变化时才会调用；主键变更不在演进范围。
     */
    SqlStatement alterColumn(String physicalTable, ColumnDdl oldColumn, ColumnDdl newColumn);

    SqlStatement renameColumn(String physicalTable, String from, String to);

    record TableDdl(String physicalName, List<ColumnDdl> columns) {
        public TableDdl {
            Objects.requireNonNull(physicalName, "physicalName");
            columns = columns == null ? List.of() : List.copyOf(columns);
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("表 " + physicalName + " 没有任何物理列");
            }
        }
    }

    record ColumnDdl(
            String name,
            String sqlType,
            boolean nullable,
            String defaultValue,
            boolean primaryKey) {

        public ColumnDdl {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sqlType, "sqlType");
        }
    }

    record IndexDdl(
            String name,
            List<String> columns,
            boolean unique,
            IndexType type,
            String where) {

        public IndexDdl {
            Objects.requireNonNull(name, "name");
            if (columns == null || columns.isEmpty()) {
                throw new IllegalArgumentException("索引 " + name + " 至少需要一列");
            }
            columns = List.copyOf(columns);
            type = type == null ? IndexType.BTREE : type;
            where = where == null || where.isBlank() ? null : where;
        }
    }
}
