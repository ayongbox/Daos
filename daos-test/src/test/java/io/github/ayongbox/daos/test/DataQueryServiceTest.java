package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.DataScopeType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MaskType;
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
import io.github.ayongbox.daos.core.metadata.permission.MaskRule;
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
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriteException;
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
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private DataQueryService service;
    private QueryRequest listTitles;
    private InMemoryAuditStore auditStore;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_query;DB_CLOSE_DELAY=-1");
        DataAccessFacade dataAccess = new DataAccessFacade(dataSource);

        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"app_demo_users\""));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_users\" (\"id\" varchar(26) PRIMARY KEY, "
                        + "\"tenant_id\" varchar(36), \"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, "
                        + "\"title\" varchar(255), \"owner\" varchar(36), \"secret\" varchar(255), "
                        + "\"phone\" varchar(32))"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_users\" "
                        + "(\"id\", \"tenant_id\", \"deleted\", \"title\", \"owner\", \"secret\", \"phone\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "1", "t1", false, "alpha", "user-1", "s1", "13800138000"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_users\" "
                        + "(\"id\", \"tenant_id\", \"deleted\", \"title\", \"owner\", \"secret\", \"phone\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "2", "t2", false, "beta", "user-2", "s2", "13900139000"));

        MetadataStore metadataStore = new InMemoryMetadataStore();
        SnapshotStore snapshotStore = new InMemorySnapshotStore();
        metadataStore.saveDraft(app());
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");

        service = new DataQueryService(
                snapshotStore, dataAccess, new PostgresDialect(), new AuditLog(auditStore = new InMemoryAuditStore()));
        listTitles = new QueryRequest(
                List.of("title"), null,
                List.of(new QueryRequest.SortSpec("title", SortDirection.ASC)), 1, 20);
    }

    @Test
    void queriesActiveSnapshotWithTenantIsolation() {
        List<Map<String, Object>> rows = service.query(
                APP_ID, "t1", listTitles, context("t1", "user-1", null, null));

        assertEquals(1, rows.size());
        assertEquals("alpha", rows.get(0).get("title"));
    }

    @Test
    void crossTenantContextSeesAllRows() {
        List<Map<String, Object>> rows = service.query(
                APP_ID, "t1", listTitles, context("", "root", null, null));

        assertEquals(2, rows.size());
        assertEquals(Set.of("alpha", "beta"), Set.copyOf(
                rows.stream().map(row -> (String) row.get("title")).toList()));
    }

    @Test
    void selfScopeOnlyReturnsOwnRows() {
        DataScope scope = new DataScope(
                DataScopeType.SELF, "f2", null, List.of(), List.of(), List.of(), false, false);

        List<Map<String, Object>> rows = service.query(
                APP_ID, "t1", listTitles, context("t1", "user-1", scope, null));

        assertEquals(1, rows.size());
        assertEquals("alpha", rows.get(0).get("title"));
    }

    @Test
    void emptyPermissionsReturnNoRows() {
        PermissionContext denied = new PermissionContext(
                "t1", "user-1", List.of(), List.of(), List.of());

        List<Map<String, Object>> rows = service.query(APP_ID, "t1", listTitles, denied);

        assertTrue(rows.isEmpty());
    }

    @Test
    void hiddenFieldFilterIsRejected() {
        FieldMask mask = new FieldMask(List.of("f3"), List.of(), List.of(), List.of());
        QueryRequest filterSecret = new QueryRequest(
                List.of("title"),
                new FilterNode.Compare("secret", ConditionOperator.EQ, List.of("s1")),
                null, 1, 20);

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> service.query(APP_ID, "t1", filterSecret, context("t1", "user-1", null, mask)));

        assertTrue(error.getMessage().contains("FIELD_HIDDEN"));
    }

    @Test
    void masksPhoneInQueryResult() {
        FieldMask mask = new FieldMask(
                List.of(), List.of(), List.of(),
                List.of(new MaskRule("f4", MaskType.PHONE, null, null)));
        QueryRequest request = new QueryRequest(List.of("title", "phone"), null, null, 1, 20);

        List<Map<String, Object>> rows = service.query(
                APP_ID, "t1", request, context("t1", "user-1", null, mask));

        assertEquals(1, rows.size());
        assertEquals("alpha", rows.get(0).get("title"));
        assertEquals("138****8000", rows.get(0).get("phone"));
    }

    @Test
    void recordsSuccessfulQueryAudit() {
        service.query(APP_ID, "t1", listTitles, context("t1", "user-1", null, null));

        List<AuditEvent> events = auditStore.recent("t1", 10);
        assertEquals(1, events.size());
        AuditEvent event = events.get(0);
        assertEquals(AuditAction.READ, event.action());
        assertEquals(AuditOutcome.SUCCESS, event.outcome());
        assertEquals("t1", event.resourceId());
        assertEquals("app-1", event.appId());
        assertEquals("user-1", event.subjectId());
        assertEquals(1, event.detail().get("rowCount"));
    }

    @Test
    void recordsDeniedQueryAudit() {
        FieldMask mask = new FieldMask(List.of("f3"), List.of(), List.of(), List.of());
        QueryRequest filterSecret = new QueryRequest(
                List.of("title"),
                new FilterNode.Compare("secret", ConditionOperator.EQ, List.of("s1")),
                null, 1, 20);

        assertThrows(PermissionRewriteException.class,
                () -> service.query(APP_ID, "t1", filterSecret, context("t1", "user-1", null, mask)));

        List<AuditEvent> events = auditStore.recent("t1", 10);
        assertEquals(1, events.size());
        assertEquals(AuditOutcome.DENIED, events.get(0).outcome());
        assertEquals(AuditAction.READ, events.get(0).action());
    }

    @Test
    void rejectsAppWithoutActiveRelease() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.query("app-missing", "t1", listTitles,
                        context("t1", "user-1", null, null)));

        assertTrue(error.getMessage().contains("没有生效版本"));
    }

    private static PermissionContext context(String tenant, String subject, DataScope scope, FieldMask mask) {
        return new PermissionContext(
                tenant, subject, List.of(), List.of(), List.of(permission(scope, mask)));
    }

    private static SchemaPermission permission(DataScope scope, FieldMask mask) {
        return new SchemaPermission(
                "p1", APP_ID, "read-users", "读取用户", null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, "users", List.of()),
                List.of(ActionType.READ), EffectType.ALLOW, null, scope, mask,
                100, true, null, null, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaApp app() {
        SchemaField title = field("f1", "title");
        SchemaField owner = field("f2", "owner");
        SchemaField secret = field("f3", "secret");
        SchemaField phone = field("f4", "phone");

        SchemaTable users = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), List.of(title, owner, secret, phone),
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(users), List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField field(String id, String code) {
        return new SchemaField(
                id, APP_ID, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                new FieldConstraints(false, false, false, false, true, true,
                        false, false, true, null, null, false, null),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
