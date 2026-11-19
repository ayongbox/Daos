package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.PermissionContext;
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
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataWriteService;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriteException;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollupServiceTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "rollup";
    private static final String TENANT = "t1";
    private static final String PARENT_ID = "t_parent";
    private static final String CHILD_ID = "t_child";
    private static final String PARENT_TABLE = "app_rollup_orders";
    private static final String CHILD_TABLE = "app_rollup_order_items";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private DataAccessFacade dataAccess;
    private DataWriteService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_rollup;DB_CLOSE_DELAY=-1");
        dataAccess = new DataAccessFacade(dataSource);

        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"" + CHILD_TABLE + "\""));
        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"" + PARENT_TABLE + "\""));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"" + PARENT_TABLE + "\" ("
                        + "\"id\" varchar(26) PRIMARY KEY, \"tenant_id\" varchar(36) NOT NULL, "
                        + "\"created_by\" varchar(36), \"created_at\" timestamp, "
                        + "\"updated_by\" varchar(36), \"updated_at\" timestamp, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, "
                        + "\"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"title\" varchar(255), \"total_amount\" decimal(20,2), "
                        + "\"average_amount\" decimal(20,2), \"item_count\" bigint, "
                        + "\"max_quantity\" bigint, \"min_quantity\" bigint)"));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"" + CHILD_TABLE + "\" ("
                        + "\"id\" varchar(26) PRIMARY KEY, \"tenant_id\" varchar(36) NOT NULL, "
                        + "\"created_by\" varchar(36), \"created_at\" timestamp, "
                        + "\"updated_by\" varchar(36), \"updated_at\" timestamp, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, "
                        + "\"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"amount\" decimal(20,2), \"quantity\" bigint, "
                        + "\"order_ref\" varchar(26))"));

        MetadataStore metadataStore = new InMemoryMetadataStore();
        SnapshotStore snapshotStore = new InMemorySnapshotStore();
        metadataStore.saveDraft(app());
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");
        service = new DataWriteService(snapshotStore, dataAccess, new PostgresDialect());
    }

    @Test
    void insertingChildrenRecalculatesSumAverageAndCount() {
        String parentId = insertParent();

        insertChild(parentId, "10.00", 2L);
        Map<String, Object> afterFirst = parentRow(parentId);
        assertEquals(new BigDecimal("10.00"), afterFirst.get("total_amount"));
        assertEquals(new BigDecimal("10.00"), afterFirst.get("average_amount"));
        assertEquals(1L, ((Number) afterFirst.get("item_count")).longValue());

        insertChild(parentId, "20.00", 4L);
        Map<String, Object> afterSecond = parentRow(parentId);
        assertEquals(new BigDecimal("30.00"), afterSecond.get("total_amount"));
        assertEquals(new BigDecimal("15.00"), afterSecond.get("average_amount"));
        assertEquals(2L, ((Number) afterSecond.get("item_count")).longValue());
    }

    @Test
    void updatingSourceAndDeletingLastChildRecalculatesEmptyAsZero() {
        String parentId = insertParent();
        String childId = insertChild(parentId, "10.00", 2L);

        service.update(APP_ID, CHILD_ID, childId, 0L,
                Map.of("amount", new BigDecimal("30.50")), allPermissions());
        Map<String, Object> updated = parentRow(parentId);
        assertEquals(new BigDecimal("30.50"), updated.get("total_amount"));
        assertEquals(new BigDecimal("30.50"), updated.get("average_amount"));

        service.delete(APP_ID, CHILD_ID, childId, 1L, allPermissions());
        Map<String, Object> emptied = parentRow(parentId);
        assertEquals(new BigDecimal("0.00"), emptied.get("total_amount"));
        assertEquals(new BigDecimal("0.00"), emptied.get("average_amount"));
        assertEquals(0L, ((Number) emptied.get("item_count")).longValue());
        assertEquals(0L, ((Number) emptied.get("max_quantity")).longValue());
        assertEquals(0L, ((Number) emptied.get("min_quantity")).longValue());
    }

    @Test
    void insertingAndDeletingChildrenRecalculatesMaxAndMin() {
        String parentId = insertParent();
        insertChild(parentId, "10.00", 9L);
        String minimumChildId = insertChild(parentId, "20.00", 3L);

        Map<String, Object> aggregated = parentRow(parentId);
        assertEquals(9L, ((Number) aggregated.get("max_quantity")).longValue());
        assertEquals(3L, ((Number) aggregated.get("min_quantity")).longValue());

        service.delete(APP_ID, CHILD_ID, minimumChildId, 0L, allPermissions());
        Map<String, Object> remaining = parentRow(parentId);
        assertEquals(9L, ((Number) remaining.get("max_quantity")).longValue());
        assertEquals(9L, ((Number) remaining.get("min_quantity")).longValue());
    }

    @Test
    void missingParentUpdatePermissionRollsBackChildInsert() {
        String parentId = insertParent();
        PermissionContext permissions = permissions(
                List.of(ActionType.READ, ActionType.CREATE), allActions());

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> insertChild(parentId, "10.00", 2L, permissions));

        assertTrue(error.getMessage().contains("orders"));
        assertTrue(error.getMessage().contains("update"));
        assertEquals(0, childCount());
        assertEquals(0L, ((Number) parentRow(parentId).get("version")).longValue());
    }

    @Test
    void parentInsertOverwritesForgedRollupValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", "order");
        values.put("total_amount", new BigDecimal("999.00"));
        values.put("average_amount", new BigDecimal("999.00"));
        values.put("item_count", 99L);
        values.put("max_quantity", 99L);
        values.put("min_quantity", 99L);

        String parentId = service.insert(APP_ID, PARENT_ID, values, allPermissions());

        Map<String, Object> row = parentRow(parentId);
        assertEquals(new BigDecimal("0.00"), row.get("total_amount"));
        assertEquals(new BigDecimal("0.00"), row.get("average_amount"));
        assertEquals(0L, ((Number) row.get("item_count")).longValue());
        assertEquals(0L, ((Number) row.get("max_quantity")).longValue());
        assertEquals(0L, ((Number) row.get("min_quantity")).longValue());
    }

    private String insertParent() {
        return service.insert(APP_ID, PARENT_ID, Map.of("title", "order"), allPermissions());
    }

    private String insertChild(String parentId, String amount, long quantity) {
        return insertChild(parentId, amount, quantity, allPermissions());
    }

    private String insertChild(
            String parentId, String amount, long quantity, PermissionContext permissions) {
        return service.insert(APP_ID, CHILD_ID, Map.of(
                "order_ref", parentId,
                "amount", new BigDecimal(amount),
                "quantity", quantity), permissions);
    }

    private Map<String, Object> parentRow(String id) {
        List<Map<String, Object>> rows = dataAccess.query(new SqlStatement(
                "SELECT * FROM \"" + PARENT_TABLE + "\" WHERE \"id\" = ?", List.of(id)));
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private int childCount() {
        List<Map<String, Object>> rows = dataAccess.query(SqlStatement.of(
                "SELECT count(*) AS \"count\" FROM \"" + CHILD_TABLE + "\""));
        return ((Number) rows.get(0).get("count")).intValue();
    }

    private static PermissionContext allPermissions() {
        return permissions(allActions(), allActions());
    }

    private static PermissionContext permissions(
            List<ActionType> parentActions, List<ActionType> childActions) {
        return new PermissionContext(
                TENANT, "user-1", List.of(), List.of(), List.of(
                        permission("p-parent", "orders", parentActions),
                        permission("p-child", "order_items", childActions)));
    }

    private static List<ActionType> allActions() {
        return List.of(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE);
    }

    private static SchemaPermission permission(
            String id, String tableCode, List<ActionType> actions) {
        return new SchemaPermission(
                id, APP_ID, "perm-" + tableCode, "权限 " + tableCode, null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, tableCode, List.of()),
                actions, EffectType.ALLOW, null, null, null,
                100, true, null, null, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaApp app() {
        SchemaField title = field(PARENT_ID, "f-title", "title", FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false));
        SchemaField sum = rollup("f-sum", "total_amount", "f-amount", "sum", 2);
        SchemaField avg = rollup("f-avg", "average_amount", "f-amount", "avg", 2);
        SchemaField count = field(PARENT_ID, "f-count", "item_count", FieldLogicalType.ROLLUP,
                new FieldConfig.RollupConfig("f-relation", null, "count", null, 0, true));
        SchemaField max = rollup("f-max", "max_quantity", "f-quantity", "max", 0);
        SchemaField min = rollup("f-min", "min_quantity", "f-quantity", "min", 0);

        SchemaField amount = field(CHILD_ID, "f-amount", "amount", FieldLogicalType.DECIMAL,
                new FieldConfig.DecimalConfig(20, 2, null, null, null, null, null, false));
        SchemaField quantity = field(CHILD_ID, "f-quantity", "quantity", FieldLogicalType.NUMBER,
                new FieldConfig.NumberConfig(null, null, 1, 0, null, false));
        SchemaField relation = field(CHILD_ID, "f-relation", "order_ref", FieldLogicalType.RELATION,
                new FieldConfig.RelationConfig(
                        PARENT_ID, null, false, 0, null, null, false, null, null));

        SchemaTable parent = table(PARENT_ID, "orders", "订单",
                List.of(title, sum, avg, count, max, min));
        SchemaTable child = table(CHILD_ID, "order_items", "订单明细",
                List.of(amount, quantity, relation));
        return new SchemaApp(
                APP_ID, APP_CODE, "汇总应用", null, null, MetadataStatus.DRAFT,
                List.of(parent, child), List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField rollup(
            String id, String code, String sourceFieldId, String aggregate, int precision) {
        return field(PARENT_ID, id, code, FieldLogicalType.ROLLUP,
                new FieldConfig.RollupConfig(
                        "f-relation", sourceFieldId, aggregate, null, precision, true));
    }

    private static SchemaTable table(
            String id, String code, String displayName, List<SchemaField> fields) {
        return new SchemaTable(
                id, APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                code, displayName, null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields,
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField field(
            String tableId, String id, String code, FieldLogicalType type, FieldConfig config) {
        return new SchemaField(
                id, APP_ID, tableId, code, code, null, type, config,
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
