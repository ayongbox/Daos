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
import io.github.ayongbox.daos.runtime.engine.SchemaEvolutionService;
import io.github.ayongbox.daos.runtime.engine.ddl.DefaultMigrationCompiler;
import io.github.ayongbox.daos.runtime.engine.dialect.H2Dialect;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaEvolutionServiceTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";
    private static final String USERS_TABLE = "app_demo_users";

    private JdbcDataSource dataSource;
    private DataAccessFacade dataAccess;
    private InMemoryMetadataStore metadataStore;
    private InMemorySnapshotStore snapshotStore;
    private InMemoryMigrationStore migrationStore;
    private SchemaEvolutionService service;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_evolution_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataAccess = new DataAccessFacade(dataSource);
        metadataStore = new InMemoryMetadataStore();
        snapshotStore = new InMemorySnapshotStore();
        migrationStore = new InMemoryMigrationStore();
        service = new SchemaEvolutionService(
                metadataStore, snapshotStore, migrationStore,
                new DefaultMigrationCompiler(), dataAccess, new H2Dialect());
    }

    @Test
    void firstReleaseCreatesTableAndRollbackIsRejected() {
        metadataStore.saveDraft(app(1, List.of(titleField())));

        MigrationRecord record = service.apply(APP_ID, "tester");

        assertEquals(MigrationStatus.APPLIED, record.status());
        assertTrue(columnExists("title"));
        assertEquals(1, snapshotStore.findActive(APP_ID).orElseThrow().releaseVersion());

        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"" + USERS_TABLE + "\" (\"id\",\"tenant_id\",\"deleted\",\"version\",\"title\") "
                        + "VALUES ('r-1','t1',FALSE,0,'alpha')"));
        assertEquals(1, countRows());

        assertThrows(IllegalStateException.class,
                () -> service.rollback(APP_ID, record.id(), "tester"));
        assertEquals(MigrationStatus.APPLIED, migrationStore.findById(record.id())
                .orElseThrow().status());
    }

    @Test
    void attachmentColumnCanBePublishedOnH2() {
        metadataStore.saveDraft(app(1, List.of(titleField(), attachmentField())));

        MigrationRecord record = service.apply(APP_ID, "tester");

        assertEquals(MigrationStatus.APPLIED, record.status());
        assertTrue(columnExists("files"));
    }

    @Test
    void addColumnAppliesAndRollsBack() {
        metadataStore.saveDraft(app(1, List.of(titleField())));
        service.apply(APP_ID, "tester");

        metadataStore.saveDraft(app(2, List.of(titleField(), summaryField())));
        MigrationPlan plan = service.preview(APP_ID);
        assertEquals(1, plan.steps().size());
        assertEquals(MigrationPlan.Operation.ADD_COLUMN, plan.steps().get(0).operation());

        MigrationRecord record = service.apply(APP_ID, "tester");

        assertTrue(columnExists("summary"));
        assertEquals(2, snapshotStore.findActive(APP_ID).orElseThrow().releaseVersion());

        service.rollback(APP_ID, record.id(), "tester");

        assertFalse(columnExists("summary"));
        assertTrue(columnExists("title"));
        assertEquals(1, snapshotStore.findActive(APP_ID).orElseThrow().releaseVersion());
        assertEquals(MigrationStatus.ROLLED_BACK, migrationStore.findById(record.id())
                .orElseThrow().status());
    }

    @Test
    void formulaColumnCanBeAddedToExistingTable() {
        metadataStore.saveDraft(app(1, List.of(titleField())));
        service.apply(APP_ID, "tester");

        metadataStore.saveDraft(app(2, List.of(titleField(), formulaField())));
        MigrationPlan plan = service.preview(APP_ID);
        assertEquals(1, plan.steps().size());
        assertEquals(MigrationPlan.Operation.ADD_COLUMN, plan.steps().get(0).operation());
        assertTrue(plan.steps().get(0).forwardSql().contains("numeric(20,2)"));

        MigrationRecord record = service.apply(APP_ID, "tester");

        assertEquals(MigrationStatus.APPLIED, record.status());
        assertTrue(columnExists("total"));
    }

    @Test
    void rollbackRequiresLatestMigration() {
        metadataStore.saveDraft(app(1, List.of(titleField())));
        service.apply(APP_ID, "tester");
        metadataStore.saveDraft(app(2, List.of(titleField(), summaryField())));
        MigrationRecord first = service.apply(APP_ID, "tester");

        metadataStore.saveDraft(app(3, List.of(titleField(), summaryField(), ownerField())));
        service.apply(APP_ID, "tester");

        assertThrows(IllegalStateException.class,
                () -> service.rollback(APP_ID, first.id(), "tester"));
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
        return field("f-1", "title");
    }

    private static SchemaField summaryField() {
        return field("f-2", "summary");
    }

    private static SchemaField ownerField() {
        return field("f-3", "owner");
    }

    private static SchemaField formulaField() {
        return new SchemaField(
                "f-5", APP_ID, "t1", "total", "合计", null,
                FieldLogicalType.FORMULA,
                new FieldConfig.FormulaConfig("1 + 1", "decimal", 2, "REALTIME"),
                FieldConstraints.defaults(),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField attachmentField() {
        return new SchemaField(
                "f-4", APP_ID, "t1", "files", "附件", null,
                FieldLogicalType.ATTACHMENT,
                new FieldConfig.AttachmentConfig(5, 10, List.of(), "local", false, false, false, true),
                FieldConstraints.defaults(),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField field(String id, String code) {
        return new SchemaField(
                id, APP_ID, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                FieldConstraints.defaults(),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
