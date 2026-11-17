package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryRequest;
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
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataQueryServiceTest {

    private static final String APP_ID = "app-1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private DataQueryService service;
    private QueryRequest listTitles;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_query;DB_CLOSE_DELAY=-1");
        DataAccessFacade dataAccess = new DataAccessFacade(dataSource);

        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"app_demo_users\""));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_users\" (\"id\" varchar(26) PRIMARY KEY, "
                        + "\"tenant_id\" varchar(36), \"title\" varchar(255))"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_users\" (\"id\", \"tenant_id\", \"title\") VALUES (?, ?, ?)",
                "1", "t1", "alpha"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_users\" (\"id\", \"tenant_id\", \"title\") VALUES (?, ?, ?)",
                "2", "t2", "beta"));

        MetadataStore metadataStore = new InMemoryMetadataStore();
        SnapshotStore snapshotStore = new InMemorySnapshotStore();
        metadataStore.saveDraft(app());
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");

        service = new DataQueryService(snapshotStore, dataAccess, new PostgresDialect());
        listTitles = new QueryRequest(
                List.of("title"), null,
                List.of(new QueryRequest.SortSpec("title", SortDirection.ASC)), 1, 20);
    }

    @Test
    void queriesActiveSnapshotWithTenantIsolation() {
        List<Map<String, Object>> rows = service.query(
                APP_ID, "t1", listTitles,
                new PermissionContext("t1", "user-1", List.of(), List.of(), List.of()));

        assertEquals(1, rows.size());
        assertEquals("alpha", rows.get(0).get("title"));
    }

    @Test
    void crossTenantContextSeesAllRows() {
        List<Map<String, Object>> rows = service.query(
                APP_ID, "t1", listTitles,
                new PermissionContext("", "root", List.of(), List.of(), List.of()));

        assertEquals(2, rows.size());
        assertEquals(Set.of("alpha", "beta"), Set.copyOf(
                rows.stream().map(row -> (String) row.get("title")).toList()));
    }

    @Test
    void rejectsAppWithoutActiveRelease() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.query("app-missing", "t1", listTitles,
                        new PermissionContext("t1", "user-1", List.of(), List.of(), List.of())));

        assertTrue(error.getMessage().contains("没有生效版本"));
    }

    private static SchemaApp app() {
        SchemaField title = new SchemaField(
                "f1", APP_ID, "t1", "title", "标题", null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                new FieldConstraints(false, false, false, false, true, true,
                        false, false, true, null, null, false, null),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        SchemaTable users = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), List.of(title),
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        return new SchemaApp(
                APP_ID, "demo", "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(users), List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
