package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.common.SubjectType;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.spi.IdentityStore;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import io.github.ayongbox.daos.runtime.identity.InMemoryIdentityStore;
import io.github.ayongbox.daos.runtime.identity.PermissionContextFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionContextFactoryTest {

    private static final String TENANT = "t1";
    private static final String APP = "app1";

    private PermissionContextFactory factory;

    @BeforeEach
    void setUp() {
        List<OrgDepartment> departments = List.of(
                department("d1", null, "/d1/", true),
                department("d2", "d1", "/d1/d2/", true),
                department("d21", "d2", "/d1/d2/d21/", true),
                department("d3", null, "/d3/", true),
                department("d4", null, "/d4/", false));

        List<OrgMember> members = List.of(
                member("m1", "d2", MemberStatus.ACTIVE),
                member("m2", "d2", MemberStatus.DISABLED),
                member("m3", null, MemberStatus.ACTIVE));

        List<RoleDefinition> roles = List.of(
                role("r_p", null, RoleScope.PLATFORM, true),
                role("r_a", APP, RoleScope.APPLICATION, true),
                role("r_x", "app2", RoleScope.APPLICATION, true),
                role("r_d", null, RoleScope.PLATFORM, false));

        List<RoleBinding> bindings = List.of(
                binding("b1", "r_p", "m1"),
                binding("b2", "r_a", "m1"),
                binding("b3", "r_x", "m1"),
                binding("b4", "r_d", "m1"),
                binding("b5", "r_a", "m2"));

        IdentityStore store = new InMemoryIdentityStore(departments, members, roles, bindings);
        factory = new PermissionContextFactory(store);
    }

    @Test
    void expandsDepartmentSubtreeAndFiltersAppRoles() {
        PermissionContext context = factory.create(TENANT, APP, "m1", List.of());

        assertEquals(List.of("d2", "d21"), context.deptIds());
        assertEquals(List.of("r_a", "r_p"), context.roleIds());
        assertEquals("m1", context.subjectId());
        assertEquals(TENANT, context.tenantId());
    }

    @Test
    void excludesDisabledAndDeletedRoles() {
        PermissionContext context = factory.create(TENANT, APP, "m1", List.of());

        assertTrue(context.roleIds().stream().noneMatch("r_d"::equals));
    }

    @Test
    void memberWithoutDepartmentGetsEmptyScope() {
        PermissionContext context = factory.create(TENANT, APP, "m3", List.of());

        assertTrue(context.deptIds().isEmpty());
    }

    @Test
    void rejectsDisabledMember() {
        assertThrows(AccessDeniedException.class,
                () -> factory.create(TENANT, APP, "m2", List.of()));
    }

    @Test
    void rejectsMemberFromAnotherTenant() {
        assertThrows(AccessDeniedException.class,
                () -> factory.create("t2", APP, "m1", List.of()));
    }

    @Test
    void keepsSuppliedPermissions() {
        SchemaPermission permission = new SchemaPermission(
                "p1", APP, "order_read", "订单读取", null,
                new PermissionSubject(SubjectType.USER, "m1", false),
                new ResourceRef(ResourceType.TABLE, APP, "t_order", List.of()),
                List.of(ActionType.READ), null, null, null, FieldMask.empty(),
                100, true, null, null, false, Map.of(), null, null, null, null, false, 1);

        PermissionContext context = factory.create(TENANT, APP, "m1", List.of(permission));

        assertEquals(1, context.permissions().size());
    }

    private static OrgDepartment department(String id, String parentId, String path, boolean enabled) {
        return new OrgDepartment(
                id, TENANT, parentId, id, "部门" + id, path, 0, enabled,
                Map.of(), null, null, null, null, false, 1);
    }

    private static OrgMember member(String id, String deptId, MemberStatus status) {
        return new OrgMember(
                id, TENANT, id, "成员" + id, null, null, deptId, status,
                Map.of(), null, null, null, null, false, 1);
    }

    private static RoleDefinition role(String id, String appId, RoleScope scope, boolean enabled) {
        return new RoleDefinition(
                id, TENANT, appId, id, "角色" + id, scope, null, enabled,
                Map.of(), null, null, null, null, false, 1);
    }

    private static RoleBinding binding(String id, String roleId, String memberId) {
        return new RoleBinding(
                id, TENANT, roleId, memberId, Map.of(), null, null, null, null, false, 1);
    }
}
