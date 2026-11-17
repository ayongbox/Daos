package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.ir.PermissionContext;
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
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import io.github.ayongbox.daos.runtime.identity.InMemoryIdentityStore;
import io.github.ayongbox.daos.runtime.identity.PermissionContextFactory;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPermissionServiceTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TENANT = "t1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private SessionPermissionService service;

    @BeforeEach
    void setUp() {
        MetadataStore metadataStore = new InMemoryMetadataStore();
        SnapshotStore snapshotStore = new InMemorySnapshotStore();
        metadataStore.saveDraft(app());
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");

        OrgMember member = new OrgMember(
                "m-1", TENANT, "alice", "Alice", null, null, null, MemberStatus.ACTIVE,
                Map.of(), "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        RoleDefinition role = new RoleDefinition(
                "r-1", TENANT, null, "readers", "只读", RoleScope.PLATFORM, null, true, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        RoleBinding binding = new RoleBinding(
                "b-1", TENANT, "r-1", "m-1", Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        InMemoryIdentityStore identityStore = new InMemoryIdentityStore(
                null, List.of(member), List.of(role), List.of(binding));
        service = new SessionPermissionService(
                snapshotStore, new PermissionContextFactory(identityStore));
    }

    @Test
    void resolvesContextFromSessionAndActiveSnapshot() {
        PermissionContext context = service.resolve(session("m-1"), APP_ID);

        assertEquals(TENANT, context.tenantId());
        assertEquals("m-1", context.subjectId());
        assertEquals(List.of("r-1"), context.roleIds());
        assertEquals(1, context.permissions().size());
        assertEquals("read-users", context.permissions().get(0).code());
    }

    @Test
    void rejectsAccountWithoutMember() {
        AccessDeniedException error = assertThrows(AccessDeniedException.class,
                () -> service.resolve(session(null), APP_ID));

        assertTrue(error.getMessage().contains("未关联组织成员"));
    }

    @Test
    void rejectsAppWithoutActiveRelease() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.resolve(session("m-1"), "app-missing"));

        assertTrue(error.getMessage().contains("没有生效版本"));
    }

    private static Session session(String memberId) {
        Instant now = Instant.parse(TIMESTAMP);
        return new Session(
                "s-1", TENANT, "acc-1", "alice", memberId, "hash",
                now, now.plusSeconds(3600), null, null);
    }

    private static SchemaApp app() {
        SchemaField title = new SchemaField(
                "f1", APP_ID, "t1", "title", "标题", null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                FieldConstraints.defaults(), FieldUiHint.empty(),
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

        SchemaPermission readUsers = new SchemaPermission(
                "p1", APP_ID, "read-users", "读取用户", null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, "users", List.of()),
                List.of(ActionType.READ), EffectType.ALLOW, null, null, null,
                100, true, null, null, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(users), List.of(), List.of(readUsers), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
