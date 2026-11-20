package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.file.FileObject;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.core.migration.MigrationRecord;
import io.github.ayongbox.daos.core.migration.MigrationStatus;
import io.github.ayongbox.daos.core.pipeline.Dialect;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcAccountStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcAuditStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcFileStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcIdentityStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcMetadataStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcMigrationStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcSessionStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcSnapshotStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcStoreSchema;
import io.github.ayongbox.daos.runtime.store.jdbc.JsonCodec;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 持久化仓储回归：每个用例写入后重新创建 Store 实例，模拟进程重启后读取。
 */
class JdbcStorePersistenceTest {

    private static final String TENANT = "t1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private JdbcTemplate jdbc;
    private JsonCodec codec;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_persistence_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        new JdbcStoreSchema(dataSource, Dialect.H2);
        jdbc = new JdbcTemplate(dataSource);
        codec = new JsonCodec();
    }

    @Test
    void metadataDraftSurvivesNewStoreInstance() {
        JdbcMetadataStore store = new JdbcMetadataStore(jdbc, codec);
        store.saveDraft(app("app-b", "beta", "Beta"));
        store.saveDraft(app("app-a", "alpha", "Alpha"));
        store.saveDraft(app("app-c", "gamma", "Gamma", true));

        JdbcMetadataStore restarted = new JdbcMetadataStore(jdbc, codec);

        assertEquals("app-a", restarted.findDraft("app-a").orElseThrow().id());
        assertEquals(List.of("alpha", "beta"),
                restarted.findAllDrafts().stream().map(SchemaApp::code).toList());
        assertTrue(restarted.findDraft("app-c").isEmpty());
    }

    @Test
    void snapshotAppendAndActivateSurviveRestart() {
        JdbcSnapshotStore store = new JdbcSnapshotStore(jdbc, codec);
        SchemaApp app = app("app-1", "demo", "Demo");
        store.append(snapshot("s1", 1L, app));
        store.append(snapshot("s2", 2L, app));
        store.activate("app-1", "s2");

        JdbcSnapshotStore restarted = new JdbcSnapshotStore(jdbc, codec);

        assertEquals(2L, restarted.latestReleaseVersion("app-1"));
        assertEquals("s2", restarted.findActive("app-1").orElseThrow().snapshotId());
        assertEquals("Demo", restarted.findById("s1").orElseThrow().app().displayName());
    }

    @Test
    void snapshotRejectsDuplicateIdAndVersion() {
        JdbcSnapshotStore store = new JdbcSnapshotStore(jdbc, codec);
        SchemaApp app = app("app-1", "demo", "Demo");
        store.append(snapshot("s1", 1L, app));

        assertThrows(IllegalStateException.class, () -> store.append(snapshot("s1", 2L, app)));
        assertThrows(IllegalStateException.class, () -> store.append(snapshot("s2", 1L, app)));
        assertEquals(1L, store.latestReleaseVersion("app-1"));
    }

    @Test
    void migrationLedgerRoundTrips() {
        JdbcMigrationStore store = new JdbcMigrationStore(jdbc, codec);
        MigrationRecord record = new MigrationRecord(
                "m1", "app-1", null, 0L, "s1", 1L,
                MigrationStatus.APPLIED, 2, "tester", TIMESTAMP, TIMESTAMP, null);
        store.append(record);
        store.update(record.withStatus(MigrationStatus.FAILED, "2026-01-02T00:00:00Z", "boom"));

        JdbcMigrationStore restarted = new JdbcMigrationStore(jdbc, codec);

        MigrationRecord loaded = restarted.findById("m1").orElseThrow();
        assertEquals(MigrationStatus.FAILED, loaded.status());
        assertEquals("boom", loaded.error());
        assertEquals(List.of("m1"), restarted.listByApp("app-1").stream()
                .map(MigrationRecord::id).toList());
        assertThrows(IllegalStateException.class, () -> restarted.append(record));
    }

    @Test
    void identityAndAccountPersist() {
        JdbcIdentityStore store = new JdbcIdentityStore(jdbc, codec);
        OrgMember member = new OrgMember(
                "m-1", TENANT, "alice", "Alice", null, null, null,
                MemberStatus.ACTIVE, Map.of(), "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        OrgDepartment department = new OrgDepartment(
                "d-1", TENANT, null, "rd", "研发", null, 0, true, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        RoleDefinition role = new RoleDefinition(
                "r-1", TENANT, null, "owner", "Owner", RoleScope.PLATFORM, null, true, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        RoleBinding binding = new RoleBinding(
                "b-1", TENANT, "r-1", "m-1", Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        store.saveMember(member);
        store.saveDepartment(department);
        store.saveRole(role);
        store.saveBinding(binding);

        JdbcIdentityStore restartedIdentity = new JdbcIdentityStore(jdbc, codec);
        assertEquals("Alice", restartedIdentity.findMember(TENANT, "m-1").orElseThrow().displayName());
        assertEquals(1, restartedIdentity.findMembers(TENANT).size());
        assertEquals("研发", restartedIdentity.findDepartments(TENANT).get(0).name());
        assertEquals("owner", restartedIdentity.findRoles(TENANT).get(0).code());
        assertEquals(1, restartedIdentity.findBindings(TENANT, "m-1").size());

        JdbcAccountStore accounts = new JdbcAccountStore(jdbc, codec);
        accounts.save(new Account("acc-1", TENANT, "alice", "Alice", "hash", "m-1", true));

        JdbcAccountStore restartedAccounts = new JdbcAccountStore(jdbc, codec);
        assertEquals("acc-1", restartedAccounts.findByUsername(TENANT, "alice").orElseThrow().id());
        assertEquals("alice", restartedAccounts.findById(TENANT, "acc-1").orElseThrow().username());
        assertThrows(IllegalStateException.class,
                () -> restartedAccounts.save(
                        new Account("acc-2", TENANT, "alice", "Alice2", "hash", "m-2", true)));
    }

    @Test
    void sessionPersistsByTokenHash() {
        JdbcSessionStore store = new JdbcSessionStore(jdbc, codec);
        store.save(new Session(
                "sess-1", TENANT, "acc-1", "alice", "m-1", "hash-1",
                Instant.parse(TIMESTAMP), Instant.parse("2026-01-02T00:00:00Z"),
                "127.0.0.1", "junit"));

        JdbcSessionStore restarted = new JdbcSessionStore(jdbc, codec);
        assertEquals("sess-1", restarted.findByTokenHash("hash-1").orElseThrow().sessionId());

        restarted.delete("sess-1");
        assertTrue(restarted.findByTokenHash("hash-1").isEmpty());
    }

    @Test
    void auditReturnsRecentByTenant() {
        JdbcAuditStore store = new JdbcAuditStore(jdbc, codec);
        store.append(event("e1", TENANT, "2026-01-01T00:00:00Z", Map.of("rows", 1)));
        store.append(event("e2", TENANT, "2026-01-01T01:00:00Z", Map.of("rows", 2)));
        store.append(event("e3", "t2", "2026-01-01T02:00:00Z", Map.of()));

        JdbcAuditStore restarted = new JdbcAuditStore(jdbc, codec);

        List<AuditEvent> recent = restarted.recent(TENANT, 1);
        assertEquals(1, recent.size());
        assertEquals("e2", recent.get(0).id());
        assertEquals(2, restarted.recent(TENANT, 10).stream()
                .map(event -> event.detail().get("rows")).findFirst().orElseThrow());
        assertTrue(restarted.recent(TENANT, 0).isEmpty());
    }

    @Test
    void fileMetadataPersistsAndDeletes() {
        JdbcFileStore store = new JdbcFileStore(jdbc, codec);
        store.save(new FileObject(
                "f-1", TENANT, "app-1", "a.txt", "text/plain", 3, "files/a.txt", "abc", "alice", TIMESTAMP));

        JdbcFileStore restarted = new JdbcFileStore(jdbc, codec);
        assertEquals("a.txt", restarted.findById("f-1").orElseThrow().fileName());

        restarted.delete("f-1");
        assertFalse(restarted.findById("f-1").isPresent());
    }

    private static SchemaApp app(String id, String code, String displayName) {
        return app(id, code, displayName, false);
    }

    private static SchemaApp app(String id, String code, String displayName, boolean deleted) {
        return new SchemaApp(
                id, code, displayName, null, null, MetadataStatus.DRAFT,
                List.of(), List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, deleted, 1);
    }

    private static SchemaSnapshot snapshot(String id, long releaseVersion, SchemaApp app) {
        return new SchemaSnapshot(
                id, app.id(), app.code(), releaseVersion, app.schemaVersion(),
                "a".repeat(64), "tester", TIMESTAMP, null, app);
    }

    private static AuditEvent event(
            String id, String tenantId, String occurredAt, Map<String, Object> detail) {
        return new AuditEvent(
                id, tenantId, "app-1", "acc-1", AuditAction.READ, ResourceType.APP,
                "app-1", AuditOutcome.SUCCESS, "ok", "127.0.0.1", "junit", "trace",
                Instant.parse(occurredAt), detail);
    }
}
