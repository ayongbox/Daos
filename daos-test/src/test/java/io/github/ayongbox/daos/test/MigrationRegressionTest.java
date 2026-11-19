package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.MigrationPlan;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.core.migration.MigrationRecord;
import io.github.ayongbox.daos.core.migration.MigrationStatus;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.runtime.engine.SchemaEvolutionService;
import io.github.ayongbox.daos.runtime.engine.ddl.DefaultMigrationCompiler;
import io.github.ayongbox.daos.runtime.engine.dialect.H2Dialect;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemoryMigrationStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F00-05 迁移回归骨架。
 *
 * <p>固定「结构变更 → 数据保留 → 回滚语义」三段式断言：先发布基线结构并写入数据，
 * 再执行演进，最后核对数据与列状态。新增迁移能力时在这里补一条回归用例，而不是只测
 * 编译出的 SQL 文本。</p>
 *
 * <p>方言维度：数据相关用例在 H2 上真实执行；PostgreSQL/MySQL 只做编译金标准，
 * 避免测试依赖外部数据库。</p>
 */
class MigrationRegressionTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";
    private static final String USERS_TABLE = "app_demo_users";
    private static final String TITLE_INSERT =
            "INSERT INTO \"" + USERS_TABLE + "\""
                    + " (\"id\",\"tenant_id\",\"deleted\",\"version\",\"title\")"
                    + " VALUES (?, 't1', FALSE, 0, ?)";

    private JdbcDataSource dataSource;
    private DataAccessFacade dataAccess;
    private InMemoryMetadataStore metadataStore;
    private InMemorySnapshotStore snapshotStore;
    private InMemoryMigrationStore migrationStore;
    private SchemaEvolutionService service;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_migration_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataAccess = new DataAccessFacade(dataSource);
        metadataStore = new InMemoryMetadataStore();
        snapshotStore = new InMemorySnapshotStore();
        migrationStore = new InMemoryMigrationStore();
        service = serviceWith(new H2Dialect());
    }

    @Test
    void addColumnPreservesExistingRows() {
        publishBaseline(List.of(titleField()));
        insertRow("r-1", "alpha");
        insertRow("r-2", "beta");

        metadataStore.saveDraft(app(2, List.of(titleField(), textField("f-2", "summary"))));
        MigrationPlan plan = service.preview(APP_ID);
        assertEquals(
                List.of(MigrationPlan.Operation.ADD_COLUMN),
                operations(plan));

        service.apply(APP_ID, "tester");

        assertEquals(2, countRows());
        assertEquals("alpha", queryString("title", "r-1"));
        assertEquals("beta", queryString("title", "r-2"));
        assertNull(queryString("summary", "r-1"));
    }

    @Test
    void renameColumnPreservesDataAndRollbackRestoresName() {
        publishBaseline(List.of(titleField()));
        insertRow("r-1", "alpha");

        metadataStore.saveDraft(app(2, List.of(textField("f-1", "caption"))));
        MigrationPlan plan = service.preview(APP_ID);
        assertEquals(List.of(MigrationPlan.Operation.RENAME_COLUMN), operations(plan));

        MigrationRecord record = service.apply(APP_ID, "tester");

        assertFalse(columnExists("title"));
        assertTrue(columnExists("caption"));
        assertEquals("alpha", queryString("caption", "r-1"));

        service.rollback(APP_ID, record.id(), "tester");

        assertTrue(columnExists("title"));
        assertFalse(columnExists("caption"));
        assertEquals("alpha", queryString("title", "r-1"));
    }

    @Test
    void alterColumnChangesTypeWithoutLosingData() {
        publishBaseline(List.of(titleField()));
        insertRow("r-1", "alpha");

        metadataStore.saveDraft(app(2, List.of(textField("f-1", "title", 512))));
        MigrationPlan plan = service.preview(APP_ID);
        assertEquals(List.of(MigrationPlan.Operation.ALTER_COLUMN), operations(plan));

        service.apply(APP_ID, "tester");

        assertEquals("alpha", queryString("title", "r-1"));
        assertEquals(2, snapshotStore.findActive(APP_ID).orElseThrow().releaseVersion());
    }

    @Test
    void dropColumnRollbackRestoresColumnButNotData() {
        publishBaseline(List.of(titleField(), textField("f-2", "summary")));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"" + USERS_TABLE + "\""
                        + " (\"id\",\"tenant_id\",\"deleted\",\"version\",\"title\",\"summary\")"
                        + " VALUES ('r-1','t1',FALSE,0,'alpha','note')"));

        metadataStore.saveDraft(app(2, List.of(titleField())));
        MigrationPlan plan = service.preview(APP_ID);
        assertEquals(List.of(MigrationPlan.Operation.DROP_COLUMN), operations(plan));

        MigrationRecord record = service.apply(APP_ID, "tester");

        assertFalse(columnExists("summary"));
        assertEquals("alpha", queryString("title", "r-1"));

        service.rollback(APP_ID, record.id(), "tester");

        assertTrue(columnExists("summary"));
        // 删列是破坏性操作：回滚只恢复结构，原数据不回来。
        assertNull(queryString("summary", "r-1"));
    }

    @Test
    void republishingUnchangedDraftKeepsReleaseVersion() {
        publishBaseline(List.of(titleField()));

        metadataStore.saveDraft(app(1, List.of(titleField())));
        MigrationRecord record = service.apply(APP_ID, "tester");

        assertEquals(MigrationStatus.APPLIED, record.status());
        assertEquals(0, record.stepCount());
        assertEquals(1, snapshotStore.findActive(APP_ID).orElseThrow().releaseVersion());
    }

    @Test
    void failingMigrationLeavesVersionAndDataIntact() {
        publishBaseline(List.of(titleField()));
        insertRow("r-1", "dup");
        insertRow("r-2", "dup");

        // 已有重复数据上建唯一索引必然失败，用于验证失败路径不推进版本、不动数据。
        metadataStore.saveDraft(app(2, List.of(uniqueTitleField())));

        assertThrows(RuntimeException.class, () -> service.apply(APP_ID, "tester"));

        assertEquals(1, snapshotStore.findActive(APP_ID).orElseThrow().releaseVersion());
        assertEquals(1, service.history(APP_ID).stream()
                .filter(record -> record.status() == MigrationStatus.FAILED)
                .count());
        assertEquals(2, countRows());
        assertEquals("dup", queryString("title", "r-1"));
    }

    @Test
    void postgresGoldenSqlCoversRenameAddAndDrop() {
        publishBaseline(List.of(titleField(), textField("f-2", "summary")));

        metadataStore.saveDraft(app(2, List.of(
                textField("f-1", "caption"),
                textField("f-3", "note"))));

        MigrationPlan plan = serviceWith(new PostgresDialect()).preview(APP_ID);

        assertEquals(List.of(
                MigrationPlan.Operation.RENAME_COLUMN,
                MigrationPlan.Operation.ADD_COLUMN,
                MigrationPlan.Operation.DROP_COLUMN), operations(plan));
        assertEquals(
                "ALTER TABLE \"app_demo_users\" RENAME COLUMN \"title\" TO \"caption\"",
                plan.steps().get(0).forwardSql());
        assertTrue(plan.steps().get(1).forwardSql()
                .startsWith("ALTER TABLE \"app_demo_users\" ADD COLUMN \"note\""));
        assertEquals(
                "ALTER TABLE \"app_demo_users\" DROP COLUMN \"summary\"",
                plan.steps().get(2).forwardSql());
    }

    // ---------------------------------------------------------------- 工具

    private void publishBaseline(List<SchemaField> fields) {
        metadataStore.saveDraft(app(1, fields));
        MigrationRecord record = service.apply(APP_ID, "tester");
        assertEquals(MigrationStatus.APPLIED, record.status());
    }

    private SchemaEvolutionService serviceWith(SqlDialect dialect) {
        return new SchemaEvolutionService(
                metadataStore, snapshotStore, migrationStore,
                new DefaultMigrationCompiler(), dataAccess, dialect);
    }

    private static List<MigrationPlan.Operation> operations(MigrationPlan plan) {
        return plan.steps().stream().map(MigrationPlan.MigrationStep::operation).toList();
    }

    private void insertRow(String id, String title) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(TITLE_INSERT)) {
            statement.setString(1, id);
            statement.setString(2, title);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    private int countRows() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM \"" + USERS_TABLE + "\"")) {
            result.next();
            return result.getInt(1);
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    private String queryString(String column, String id) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT \"" + column + "\" FROM \"" + USERS_TABLE + "\" WHERE \"id\" = ?")) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    private boolean columnExists(String column) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                             + "WHERE TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            statement.setString(1, USERS_TABLE);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) > 0;
            }
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    private static SchemaApp app(int schemaVersion, List<SchemaField> fields) {
        SchemaTable users = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields,
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(users), List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, schemaVersion);
    }

    private static SchemaField titleField() {
        return textField("f-1", "title");
    }

    private static SchemaField uniqueTitleField() {
        return new SchemaField(
                "f-1", APP_ID, "t1", "title", "title", null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                new FieldConstraints(false, true, true, false, false, true,
                        false, false, true, null, null, false, null),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField textField(String id, String code) {
        return textField(id, code, 255);
    }

    private static SchemaField textField(String id, String code, int maxLength) {
        return new SchemaField(
                id, APP_ID, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(maxLength, 0, null, null, false),
                FieldConstraints.defaults(),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
