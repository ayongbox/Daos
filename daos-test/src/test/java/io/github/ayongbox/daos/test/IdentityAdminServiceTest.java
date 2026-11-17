package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;
import io.github.ayongbox.daos.runtime.identity.IdentityAdminService;
import io.github.ayongbox.daos.runtime.identity.InMemoryIdentityStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityAdminServiceTest {

    private static final String TENANT = "t1";
    private static final String OPERATOR = "admin";

    private InMemoryIdentityStore store;
    private InMemoryAuditStore auditStore;
    private IdentityAdminService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdentityStore(null, null, null, null);
        auditStore = new InMemoryAuditStore();
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new IdentityAdminService(store, new AuditLog(auditStore), clock);
    }

    @Test
    void createsDepartmentTreeWithMaterializedPath() {
        OrgDepartment root = service.createDepartment(TENANT, OPERATOR, null, "hq", "总部");
        OrgDepartment child = service.createDepartment(TENANT, OPERATOR, root.id(), "rd", "研发部");

        assertEquals("/" + root.id() + "/", root.path());
        assertEquals(root.path() + child.id() + "/", child.path());
        assertEquals(root.id(), child.parentId());
    }

    @Test
    void rejectsDuplicateDepartmentCode() {
        service.createDepartment(TENANT, OPERATOR, null, "hq", "总部");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createDepartment(TENANT, OPERATOR, null, "hq", "总部二"));

        assertTrue(error.getMessage().contains("已存在"));
    }

    @Test
    void rejectsUnknownParentDepartment() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createDepartment(TENANT, OPERATOR, "missing", "rd", "研发部"));
    }

    @Test
    void createsMemberUnderDepartment() {
        OrgDepartment department = service.createDepartment(TENANT, OPERATOR, null, "hq", "总部");
        OrgMember member = service.createMember(TENANT, OPERATOR, "alice", "Alice", department.id());

        assertEquals(department.id(), member.deptId());
        assertEquals(MemberStatus.ACTIVE, member.status());
        assertTrue(store.findMember(TENANT, member.id()).isPresent());
    }

    @Test
    void rejectsMemberWithUnknownDepartment() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createMember(TENANT, OPERATOR, "bob", "Bob", "missing"));
    }

    @Test
    void normalizesPlatformRoleScope() {
        RoleDefinition platform = service.createRole(
                TENANT, OPERATOR, "app-1", "admin", "管理员", RoleScope.PLATFORM);
        RoleDefinition application = service.createRole(
                TENANT, OPERATOR, "app-1", "editor", "编辑", RoleScope.APPLICATION);

        assertNull(platform.appId());
        assertEquals("app-1", application.appId());
    }

    @Test
    void bindsRoleAndWritesGrantAudit() {
        OrgMember member = service.createMember(TENANT, OPERATOR, "alice", "Alice", null);
        RoleDefinition role = service.createRole(
                TENANT, OPERATOR, null, "admin", "管理员", RoleScope.PLATFORM);

        RoleBinding binding = service.bindRole(TENANT, OPERATOR, member.id(), role.id());

        assertEquals(role.id(), binding.roleId());
        List<AuditEvent> events = auditStore.recent(TENANT, 10);
        assertEquals(1, events.size());
        AuditEvent event = events.get(0);
        assertEquals(AuditAction.GRANT, event.action());
        assertEquals(OPERATOR, event.subjectId());
        assertEquals(role.id(), event.resourceId());
        assertEquals(member.id(), event.detail().get("memberId"));
    }

    @Test
    void rejectsDuplicateBinding() {
        OrgMember member = service.createMember(TENANT, OPERATOR, "alice", "Alice", null);
        RoleDefinition role = service.createRole(
                TENANT, OPERATOR, null, "admin", "管理员", RoleScope.PLATFORM);
        service.bindRole(TENANT, OPERATOR, member.id(), role.id());

        assertThrows(IllegalArgumentException.class,
                () -> service.bindRole(TENANT, OPERATOR, member.id(), role.id()));
    }

    @Test
    void rejectsBindingDisabledMember() {
        String now = "2026-01-01T00:00:00Z";
        OrgMember disabled = new OrgMember(
                "m-1", TENANT, "bob", "Bob", null, null, null, MemberStatus.DISABLED,
                Map.of(), OPERATOR, now, OPERATOR, now, false, 1);
        store.saveMember(disabled);
        RoleDefinition role = service.createRole(
                TENANT, OPERATOR, null, "admin", "管理员", RoleScope.PLATFORM);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.bindRole(TENANT, OPERATOR, "m-1", role.id()));

        assertTrue(error.getMessage().contains("停用"));
    }
}
