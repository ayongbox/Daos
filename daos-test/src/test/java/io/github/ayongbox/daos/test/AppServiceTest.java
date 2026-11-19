package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.runtime.app.AppService;
import io.github.ayongbox.daos.runtime.identity.ContextPermissionChecker;
import io.github.ayongbox.daos.runtime.identity.InMemoryIdentityStore;
import io.github.ayongbox.daos.runtime.identity.PermissionContextFactory;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC);

    private InMemoryMetadataStore metadataStore;
    private InMemoryIdentityStore identityStore;
    private InMemorySnapshotStore snapshotStore;
    private AppService appService;
    private SessionPermissionService permissionService;
    private Session session;

    @BeforeEach
    void setUp() {
        metadataStore = new InMemoryMetadataStore();
        identityStore = new InMemoryIdentityStore(null, null, null, null);
        identityStore.saveMember(new OrgMember(
                "member-1", "tenant-1", "alice", "Alice", null, null, null,
                MemberStatus.ACTIVE, Map.of(), "test", Instant.now(CLOCK).toString(),
                "test", Instant.now(CLOCK).toString(), false, 1));
        snapshotStore = new InMemorySnapshotStore();
        appService = new AppService(metadataStore, identityStore, snapshotStore, CLOCK);
        permissionService = new SessionPermissionService(
                snapshotStore, metadataStore, new PermissionContextFactory(identityStore));
        session = new Session(
                "session-1", "tenant-1", "subject-1", "alice", "member-1",
                "hash", Instant.now(CLOCK), Instant.now(CLOCK).plusSeconds(3600),
                null, null);
    }

    @Test
    void createBuildsOwnerRoleAndDraftPermission() {
        SchemaApp app = appService.create(
                "tenant-1", "member-1", "alice", "crm", "客户管理", null, null);

        assertEquals("crm", app.code());
        assertEquals(2, app.permissions().size());
        assertEquals(1, metadataStore.findAllDrafts().size());
        assertTrue(app.permissions().stream()
                .anyMatch(permission -> permission.resource().type() == ResourceType.TABLE
                        && permission.actions().containsAll(List.of(
                        ActionType.READ, ActionType.CREATE,
                        ActionType.UPDATE, ActionType.DELETE))));
        assertTrue(identityStore.findRoles("tenant-1").stream()
                .anyMatch(role -> role.appId().equals(app.id())));
        assertTrue(identityStore.findBindings("tenant-1", "member-1").stream()
                .anyMatch(binding -> binding.memberId().equals("member-1")));
    }

    @Test
    void unpublishedAppCanBeDesignedFromDraftPermissions() {
        SchemaApp app = appService.create(
                "tenant-1", "member-1", "alice", "crm", "客户管理", null, null);

        PermissionContext context = permissionService.resolve(session, app.id());
        ContextPermissionChecker checker = new ContextPermissionChecker(context, CLOCK);
        ResourceRef resource = new ResourceRef(
                ResourceType.APP, app.code(), app.id(), List.of());

        assertTrue(checker.isAllowed(context.subjectId(), resource, ActionType.DESIGN));
        assertTrue(checker.isAllowed(context.subjectId(), resource, ActionType.PUBLISH));

        ResourceRef tableResource = new ResourceRef(
                ResourceType.TABLE, app.code(), null, List.of());
        assertTrue(checker.isAllowed(context.subjectId(), tableResource, ActionType.CREATE));
        assertTrue(checker.isAllowed(context.subjectId(), tableResource, ActionType.READ));
    }

    @Test
    void rejectsDuplicatedAppCode() {
        appService.create("tenant-1", "member-1", "alice", "crm", "客户管理", null, null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> appService.create(
                        "tenant-1", "member-1", "alice", "crm", "客户管理 2", null, null));

        assertTrue(error.getMessage().contains("应用编码已存在"));
    }

    @Test
    void softDeletesAppAndHidesItFromLists() {
        SchemaApp app = appService.create(
                "tenant-1", "member-1", "alice", "crm", "客户管理", null, null);

        appService.delete(app.id(), "alice");

        assertTrue(metadataStore.findDraft(app.id()).isEmpty());
        assertTrue(appService.listDrafts().isEmpty());
    }

    @Test
    void generatesCodeWhenUserLeavesItBlank() {
        SchemaApp app = appService.create(
                "tenant-1", "member-1", "alice", null, "客户管理", "销售线索", "grid");

        assertTrue(app.code().startsWith("app_"));
        assertEquals(30, app.code().length());
        assertEquals("grid", app.icon());
    }
}
