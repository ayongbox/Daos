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

/**
 * 数据访问门面。只执行已经渲染完成的参数化语句，不做编译、不做权限判断。
 *
 * <p>所有业务读写必须经过该层；返回值按列别名组织，调用方可直接按字段编码取值。</p>
 */
public final class DataAccessFacade {

    private final DataSource dataSource;

    public DataAccessFacade(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public List<Map<String, Object>> query(SqlStatement statement) {
        Objects.requireNonNull(statement, "statement");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement prepared = prepare(connection, statement);
             ResultSet resultSet = prepared.executeQuery()) {
            return readRows(resultSet);
        } catch (SQLException error) {
            throw new SqlExecutionException("查询执行失败: " + statement.sql(), error);
        }
    }

    public int update(SqlStatement statement) {
        Objects.requireNonNull(statement, "statement");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement prepared = prepare(connection, statement)) {
            return prepared.executeUpdate();
        } catch (SQLException error) {
            throw new SqlExecutionException("写入执行失败: " + statement.sql(), error);
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
