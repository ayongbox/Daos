package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.SqlExecutionException;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataAccessFacadeTest {

    private DataAccessFacade facade;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_exec;DB_CLOSE_DELAY=-1");
        facade = new DataAccessFacade(dataSource);

        facade.update(SqlStatement.of("DROP TABLE IF EXISTS app_demo_users"));
        facade.update(SqlStatement.of(
                "CREATE TABLE app_demo_users (id varchar(26) PRIMARY KEY, title varchar(255), amount int)"));
    }

    @Test
    void returnsRowsKeyedByColumnAliasInQueryOrder() {
        insert("1", "b", 20);
        insert("2", "a", 10);

        List<Map<String, Object>> rows = facade.query(SqlStatement.of(
                "SELECT title AS \"title\", amount AS \"amount\" FROM app_demo_users ORDER BY amount ASC"));

        assertEquals(2, rows.size());
        assertEquals("a", rows.get(0).get("title"));
        assertEquals(10, ((Number) rows.get(0).get("amount")).intValue());
        assertEquals("b", rows.get(1).get("title"));
    }

    @Test
    void bindsParametersPositionally() {
        insert("1", "a", 10);
        insert("2", "b", 20);

        List<Map<String, Object>> rows = facade.query(SqlStatement.of(
                "SELECT id AS \"id\" FROM app_demo_users WHERE title = ? AND amount > ?", "b", 10));

        assertEquals(1, rows.size());
        assertEquals("2", rows.get(0).get("id"));
    }

    @Test
    void bindsNullParameters() {
        insert("3", null, 30);

        List<Map<String, Object>> rows = facade.query(SqlStatement.of(
                "SELECT title AS \"title\" FROM app_demo_users WHERE id = ?", "3"));

        assertEquals(1, rows.size());
        assertNull(rows.get(0).get("title"));
    }

    @Test
    void returnsAffectedRowsOnUpdate() {
        assertEquals(1, insert("1", "a", 10));

        int updated = facade.update(SqlStatement.of(
                "UPDATE app_demo_users SET title = ? WHERE id = ?", "b", "1"));

        assertEquals(1, updated);
    }

    @Test
    void wrapsExecutionFailureWithoutSwallowingCause() {
        SqlExecutionException error = assertThrows(SqlExecutionException.class,
                () -> facade.query(SqlStatement.of("SELECT * FROM missing_table")));

        assertEquals("查询执行失败: SELECT * FROM missing_table", error.getMessage());
        assertNotNull(error.getCause());
    }

    private int insert(String id, String title, int amount) {
        return facade.update(SqlStatement.of(
                "INSERT INTO app_demo_users (id, title, amount) VALUES (?, ?, ?)", id, title, amount));
    }
}
