package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.DataScopeType;
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
import io.github.ayongbox.daos.core.metadata.permission.DataScope;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataWriteService;
import io.github.ayongbox.daos.runtime.exec.WriteConflictException;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriteException;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataWriteServiceTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private DataAccessFacade dataAccess;
    private DataWriteService service;
    private InMemoryAuditStore auditStore;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_write;DB_CLOSE_DELAY=-1");
        dataAccess = new DataAccessFacade(dataSource);

        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"app_demo_users\""));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_users\" ("
                        + "\"id\" varchar(26) PRIMARY KEY, \"tenant_id\" varchar(36) NOT NULL, "
                        + "\"created_by\" varchar(36), \"created_at\" timestamp, "
                        + "\"updated_by\" varchar(36), \"updated_at\" timestamp, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, "
                        + "\"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"title\" varchar(255), \"owner\" varchar(36), \"code\" varchar(64) NOT NULL)"));

        MetadataStore metadataStore = new InMemoryMetadataStore();
        SnapshotStore snapshotStore = new InMemorySnapshotStore();
        metadataStore.saveDraft(app());
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");

        service = new DataWriteService(
                snapshotStore, dataAccess, new PostgresDialect(), new AuditLog(auditStore = new InMemoryAuditStore()));
    }

    @Test
    void insertAppliesTenantVersionAndAudit() {
        String id = service.insert(APP_ID, "t1", values("alpha", "user-1", "A1"), allPermissions(null));

        assertNotNull(id);
        Map<String, Object> row = row(id);
        assertEquals("t1", row.get("tenant_id"));
        assertEquals("alpha", row.get("title"));
        assertEquals(0L, ((Number) row.get("version")).longValue());
        assertEquals(Boolean.FALSE, row.get("deleted"));
        assertEquals("user-1", row.get("created_by"));
    }

    @Test
    void insertRejectsMissingRequiredField() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", "alpha");
        values.put("owner", "user-1");

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> service.insert(APP_ID, "t1", values, allPermissions(null)));

        assertTrue(error.getMessage().contains("必填"));
    }

    @Test
    void insertRejectsHiddenField() {
        FieldMask mask = new FieldMask(List.of("f1"), List.of(), List.of(), List.of());

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> service.insert(APP_ID, "t1", values("alpha", "user-1", "A1"), allPermissions(mask)));

        assertTrue(error.getMessage().contains("隐藏"));
    }

    @Test
    void recordsCreateAudit() {
        String id = service.insert(APP_ID, "t1", values("alpha", "user-1", "A1"), allPermissions(null));

        List<AuditEvent> events = auditStore.recent("t1", 10);
        assertEquals(1, events.size());
        AuditEvent event = events.get(0);
        assertEquals(AuditAction.CREATE, event.action());
        assertEquals(AuditOutcome.SUCCESS, event.outcome());
        assertEquals(id, event.resourceId());
        assertEquals("t1", event.detail().get("tableId"));
    }

    @Test
    void recordsDeniedCreateAudit() {
        PermissionContext readOnly = context(null, List.of(ActionType.READ));

        assertThrows(PermissionRewriteException.class,
                () -> service.insert(APP_ID, "t1", values("alpha", "user-1", "A1"), readOnly));

        List<AuditEvent> events = auditStore.recent("t1", 10);
        assertEquals(1, events.size());
        assertEquals(AuditOutcome.DENIED, events.get(0).outcome());
        assertEquals(AuditAction.CREATE, events.get(0).action());
    }

    @Test
    void insertWithoutCreatePermissionIsRejected() {
        PermissionContext readOnly = context(null, List.of(ActionType.READ));

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> service.insert(APP_ID, "t1", values("alpha", "user-1", "A1"), readOnly));

        assertTrue(error.getMessage().contains("权限"));
    }

    @Test
    void updateOnlyTouchesRecordsInScope() {
        insertRow("r1", "t1", "user-1", "alpha", "A1");
        insertRow("r2", "t2", "user-2", "beta", "B1");

        int affected = service.update(APP_ID, "t1", "r1", 0L,
                Map.of("title", "alpha-2"), selfPermissions());

        assertEquals(1, affected);
        assertEquals("alpha-2", row("r1").get("title"));
        assertEquals("beta", row("r2").get("title"));
    }

    @Test
    void updateOutsideScopeConflicts() {
        insertRow("r2", "t2", "user-2", "beta", "B1");

        assertThrows(WriteConflictException.class,
                () -> service.update(APP_ID, "t1", "r2", 0L, Map.of("title", "x"), selfPermissions()));
        assertEquals("beta", row("r2").get("title"));
    }

    @Test
    void updateWithStaleVersionConflicts() {
        insertRow("r1", "t1", "user-1", "alpha", "A1");

        assertThrows(WriteConflictException.class,
                () -> service.update(APP_ID, "t1", "r1", 9L, Map.of("title", "x"), selfPermissions()));
        assertEquals("alpha", row("r1").get("title"));
    }

    @Test
    void updateRejectsSystemColumnAndImmutableField() {
        insertRow("r1", "t1", "user-1", "alpha", "A1");

        PermissionRewriteException system = assertThrows(PermissionRewriteException.class,
                () -> service.update(APP_ID, "t1", "r1", 0L, Map.of("version", 2), selfPermissions()));
        assertTrue(system.getMessage().contains("系统列"));

        PermissionRewriteException immutable = assertThrows(PermissionRewriteException.class,
                () -> service.update(APP_ID, "t1", "r1", 0L, Map.of("code", "A2"), selfPermissions()));
        assertTrue(immutable.getMessage().contains("不可修改"));
    }

    @Test
    void deleteMarksSoftDeletedRecord() {
        insertRow("r1", "t1", "user-1", "alpha", "A1");

        int affected = service.delete(APP_ID, "t1", "r1", 0L, selfPermissions());

        assertEquals(1, affected);
        Map<String, Object> row = row("r1");
        assertEquals(Boolean.TRUE, row.get("deleted"));
        assertEquals(1L, ((Number) row.get("version")).longValue());
    }

    @Test
    void deleteOutsideScopeConflicts() {
        insertRow("r2", "t2", "user-2", "beta", "B1");

        assertThrows(WriteConflictException.class,
                () -> service.delete(APP_ID, "t1", "r2", 0L, selfPermissions()));
        assertEquals(Boolean.FALSE, row("r2").get("deleted"));
    }

    private Map<String, Object> row(String id) {
        List<Map<String, Object>> rows = dataAccess.query(SqlStatement.of(
                "SELECT * FROM \"app_demo_users\" WHERE \"id\" = ?", id));
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private void insertRow(String id, String tenant, String owner, String title, String code) {
        dataAccess.update(new SqlStatement(
                "INSERT INTO \"app_demo_users\" "
                        + "(\"id\", \"tenant_id\", \"title\", \"owner\", \"code\", \"version\", \"deleted\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                List.of(id, tenant, title, owner, code, 0L, Boolean.FALSE)));
    }

    private static Map<String, Object> values(String title, String owner, String code) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", title);
        values.put("owner", owner);
        values.put("code", code);
        return values;
    }

    private static PermissionContext selfPermissions() {
        DataScope scope = new DataScope(
                DataScopeType.SELF, "f2", null, List.of(), List.of(), List.of(), false, false);
        return context(scope, allActions());
    }

    private static PermissionContext allPermissions(FieldMask mask) {
        return context(null, allActions(), mask);
    }

    private static PermissionContext context(DataScope scope, List<ActionType> actions) {
        return context(scope, actions, null);
    }

    private static PermissionContext context(DataScope scope, List<ActionType> actions, FieldMask mask) {
        return new PermissionContext(
                "t1", "user-1", List.of(), List.of(), List.of(permission(actions, scope, mask)));
    }

    private static List<ActionType> allActions() {
        return List.of(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE);
    }

    private static SchemaPermission permission(
            List<ActionType> actions, DataScope scope, FieldMask mask) {
        return new SchemaPermission(
                "p1", APP_ID, "write-users", "写入用户", null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, "users", List.of()),
                actions, EffectType.ALLOW, null, scope, mask,
                100, true, null, null, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaApp app() {
        SchemaField title = field("f1", "title", false, false);
        SchemaField owner = field("f2", "owner", false, false);
        SchemaField code = field("f3", "code", true, true);

        SchemaTable users = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), List.of(title, owner, code),
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(users), List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField field(String id, String code, boolean required, boolean immutable) {
        return new SchemaField(
                id, APP_ID, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                new FieldConstraints(required, false, false, false, true, true,
                        immutable, false, true, null, null, false, null),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
