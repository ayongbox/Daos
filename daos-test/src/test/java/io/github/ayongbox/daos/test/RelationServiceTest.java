package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.SubjectType;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.engine.dialect.H2Dialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.relation.RelationService;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationServiceTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private RelationService relationService;
    private DataQueryService queryService;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_relation;DB_CLOSE_DELAY=-1");
        DataAccessFacade dataAccess = new DataAccessFacade(dataSource);
        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"app_demo_orders\""));
        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"app_demo_customers\""));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_customers\" (\"id\" varchar(26) PRIMARY KEY, "
                        + "\"tenant_id\" varchar(36), \"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, \"name\" varchar(255))"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_customers\" (\"id\", \"tenant_id\", \"deleted\", \"name\") "
                        + "VALUES ('c1', 't1', false, '上海客户')"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_customers\" (\"id\", \"tenant_id\", \"deleted\", \"name\") "
                        + "VALUES ('c2', 't1', false, '北京客户')"));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_orders\" (\"id\" varchar(26) PRIMARY KEY, "
                        + "\"tenant_id\" varchar(36), \"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, "
                        + "\"title\" varchar(255), \"customer\" varchar(26))"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_orders\" (\"id\", \"tenant_id\", \"deleted\", \"title\", \"customer\") "
                        + "VALUES ('o1', 't1', false, '订单A', 'c2')"));

        MetadataStore metadataStore = new InMemoryMetadataStore();
        SnapshotStore snapshotStore = new InMemorySnapshotStore();
        metadataStore.saveDraft(app());
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");

        queryService = new DataQueryService(
                snapshotStore, dataAccess, new H2Dialect(), new AuditLog(new InMemoryAuditStore()));
        relationService = new RelationService(snapshotStore, queryService);
    }

    @Test
    void listsRelationOptionsAndFiltersByKeyword() {
        List<RelationService.RelationOption> all = relationService.options(
                APP_ID, "orders", "customer", null, 20, context(true));

        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(option -> "北京客户".equals(option.label())));

        List<RelationService.RelationOption> filtered = relationService.options(
                APP_ID, "orders", "customer", "北京", 20, context(true));

        assertEquals(1, filtered.size());
        assertEquals("c2", filtered.get(0).value());
        assertEquals("北京客户", filtered.get(0).label());
    }

    @Test
    void returnsNoOptionsWhenTargetTableIsNotReadable() {
        assertTrue(relationService.options(
                APP_ID, "orders", "customer", null, 20, context(false)).isEmpty());
    }

    @Test
    void decoratesRelationColumnWithDisplayValue() {
        List<Map<String, Object>> rows = queryService.query(
                APP_ID, "t1",
                new QueryRequest(List.of("title", "customer"), null, null, 1, 10),
                context(true));

        assertEquals(1, rows.size());
        assertEquals("c2", rows.get(0).get("customer"));
        assertEquals("北京客户", rows.get(0).get("customer_display"));
    }

    @Test
    void rejectsMissingRelationOnWriteAndAcceptsExisting() {
        SchemaTable orders = app().tables().stream()
                .filter(table -> "orders".equals(table.code()))
                .findFirst()
                .orElseThrow();

        relationService.validate(APP_ID, orders, Map.of("customer", "c1"), context(true));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> relationService.validate(
                        APP_ID, orders, Map.of("customer", "missing"), context(true)));
        assertTrue(error.getMessage().contains("customer"));
    }

    private static PermissionContext context(boolean canReadCustomers) {
        List<SchemaPermission> permissions = canReadCustomers
                ? List.of(permission("p1", "orders"), permission("p2", "customers"))
                : List.of(permission("p1", "orders"));
        return new PermissionContext("t1", "user-1", List.of(), List.of(), permissions);
    }

    private static SchemaPermission permission(String id, String tableCode) {
        return new SchemaPermission(
                id, APP_ID, "read-" + tableCode, "读取" + tableCode, null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, tableCode, List.of()),
                List.of(ActionType.READ), EffectType.ALLOW, null, null, null,
                100, true, null, null, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaApp app() {
        SchemaField customerName = new SchemaField(
                "f4", APP_ID, "t2", "name", "客户名称", null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                constraints(0),
                FieldUiHint.empty(), MetadataStatus.PUBLISHED, false, true, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        SchemaTable customers = new SchemaTable(
                "t2", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "customers", "客户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, "f4", List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), List.of(customerName),
                List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        SchemaField title = new SchemaField(
                "f1", APP_ID, "t1", "title", "标题", null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                constraints(0),
                FieldUiHint.empty(), MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        SchemaField customer = new SchemaField(
                "f2", APP_ID, "t1", "customer", "客户", null,
                FieldLogicalType.RELATION,
                new FieldConfig.RelationConfig(
                        "t2", "f4", false, 1, null, null, false, null, null),
                constraints(1),
                FieldUiHint.empty(), MetadataStatus.PUBLISHED, false, false, 1, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        SchemaTable orders = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "orders", "订单", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, "f1", List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), List.of(title, customer),
                List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(orders, customers), List.of(), List.of(), List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static FieldConstraints constraints(int sortOrder) {
        return new FieldConstraints(false, false, false, false, true, true,
                false, false, true, null, null, false, null);
    }
}
