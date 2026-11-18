package io.github.ayongbox.daos.runtime.exec;

import io.github.ayongbox.daos.core.ir.SqlStatement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 数据访问门面。只执行已经渲染完成的参数化语句，不做编译、不做权限判断。
 *
 * <p>所有业务读写必须经过该层；返回值按列别名组织，调用方可直接按字段编码取值。</p>
 *
 * <p>事务通过线程绑定连接实现：同一线程内 {@link #inTransaction(Supplier)} 期间
 * 的所有读写复用同一连接，异常整体回滚。</p>
 */
public final class DataAccessFacade {

    private final DataSource dataSource;
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    public DataAccessFacade(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public List<Map<String, Object>> query(SqlStatement statement) {
        Objects.requireNonNull(statement, "statement");
        Connection connection = null;
        try {
            connection = acquire();
            try (PreparedStatement prepared = prepare(connection, statement);
                 ResultSet resultSet = prepared.executeQuery()) {
                return readRows(resultSet);
            }
        } catch (SQLException error) {
            throw new SqlExecutionException("查询执行失败: " + statement.sql(), error);
        } finally {
            closeIfOwned(connection);
        }
    }

    public int update(SqlStatement statement) {
        Objects.requireNonNull(statement, "statement");
        Connection connection = null;
        try {
            connection = acquire();
            try (PreparedStatement prepared = prepare(connection, statement)) {
                return prepared.executeUpdate();
            }
        } catch (SQLException error) {
            throw new SqlExecutionException("写入执行失败: " + statement.sql(), error);
        } finally {
            closeIfOwned(connection);
        }
    }

    /**
     * 在同一事务内执行工作单元。已处于事务中时直接复用当前连接，便于组合调用。
     */
    public <T> T inTransaction(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        if (transactionConnection.get() != null) {
            return work.get();
        }
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            transactionConnection.set(connection);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                transactionConnection.remove();
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException error) {
            throw new SqlExecutionException("事务执行失败: " + error.getMessage(), error);
        } finally {
            closeQuietly(connection);
        }
    }

    private Connection acquire() throws SQLException {
        Connection bound = transactionConnection.get();
        return bound != null ? bound : dataSource.getConnection();
    }

    private void closeIfOwned(Connection connection) {
        if (connection == null || transactionConnection.get() == connection) {
            return;
        }
        closeQuietly(connection);
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // 连接已不可用时无需覆盖原始异常。
        }
    }

    /**
     * 在同一事务内顺序执行多条语句，任一失败整体回滚。
     *
     * <p>供结构演进使用；语句必须已经渲染完成，本方法不做编译与权限判断。</p>
     */
    public void execute(List<SqlStatement> statements) {
        Objects.requireNonNull(statements, "statements");
        if (statements.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (SqlStatement statement : statements) {
                    try (PreparedStatement prepared = prepare(connection, statement)) {
                        prepared.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException error) {
                connection.rollback();
                throw new SqlExecutionException("批量执行失败，已回滚: " + error.getMessage(), error);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException error) {
            throw new SqlExecutionException("批量执行失败: " + error.getMessage(), error);
        }
    }

    private PreparedStatement prepare(Connection connection, SqlStatement statement) throws SQLException {
        PreparedStatement prepared = connection.prepareStatement(statement.sql());
        List<Object> parameters = statement.parameters();
        for (int index = 0; index < parameters.size(); index++) {
            Object value = parameters.get(index);
            if (value == null) {
                prepared.setNull(index + 1, Types.NULL);
            } else {
                prepared.setObject(index + 1, value);
            }
        }
        return prepared;
    }

    private List<Map<String, Object>> readRows(ResultSet resultSet) throws SQLException {
        ResultSetMetaData meta = resultSet.getMetaData();
        int columnCount = meta.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();

        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>(columnCount);
            for (int index = 1; index <= columnCount; index++) {
                row.put(meta.getColumnLabel(index), resultSet.getObject(index));
            }
            rows.add(row);
        }
        return rows;
    }
}
