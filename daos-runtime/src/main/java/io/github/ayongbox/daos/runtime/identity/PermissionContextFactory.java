package io.github.ayongbox.daos.runtime.identity;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.spi.IdentityStore;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从真实身份数据构建权限上下文。职责边界：
 * <ul>
 *   <li>成员必须是本租户内启用且未删除的成员；</li>
 *   <li>部门集合展开为「成员所在部门 + 其全部下级部门」，供数据范围直接使用；</li>
 *   <li>角色集合包含平台角色与当前应用角色，应用角色不会越界到其他应用。</li>
 * </ul>
 *
 * <p>权限规则由调用方从激活快照读取后传入，本类不访问草稿。</p>
 */
public final class PermissionContextFactory {

    private final IdentityStore identityStore;

    public PermissionContextFactory(IdentityStore identityStore) {
        this.identityStore = Objects.requireNonNull(identityStore, "identityStore");
    }

    public PermissionContext create(
            String tenantId, String appId, String memberId, List<SchemaPermission> permissions) {

        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(memberId, "memberId");

        OrgMember member = identityStore.findMember(tenantId, memberId)
                .orElseThrow(() -> new AccessDeniedException("成员不存在: " + memberId));
        if (!member.active()) {
            throw new AccessDeniedException("成员已停用或删除: " + memberId);
        }

        return new PermissionContext(
                tenantId,
                memberId,
                resolveRoleIds(tenantId, appId, memberId),
                resolveDeptIds(tenantId, member),
                permissions == null ? List.of() : permissions);
    }

    private List<String> resolveDeptIds(String tenantId, OrgMember member) {
        if (member.deptId() == null || member.deptId().isBlank()) {
            return List.of();
        }

        Map<String, OrgDepartment> enabled = new LinkedHashMap<>();
        identityStore.findDepartments(tenantId).stream()
                .filter(department -> !department.deleted() && department.enabled())
                .forEach(department -> enabled.put(department.id(), department));

        OrgDepartment own = enabled.get(member.deptId());
        if (own == null) {
            return List.of();
        }

        return enabled.values().stream()
                .filter(department -> department.path().startsWith(own.path()))
                .sorted(Comparator.comparing(OrgDepartment::path).thenComparing(OrgDepartment::id))
                .map(OrgDepartment::id)
                .distinct()
                .toList();
    }

    private List<String> resolveRoleIds(String tenantId, String appId, String memberId) {
        Map<String, RoleDefinition> usable = new LinkedHashMap<>();
        identityStore.findRoles(tenantId).stream()
                .filter(role -> !role.deleted() && role.enabled())
                .filter(role -> role.scope() == RoleScope.PLATFORM || Objects.equals(role.appId(), appId))
                .forEach(role -> usable.put(role.id(), role));

        return identityStore.findBindings(tenantId, memberId).stream()
                .filter(binding -> !binding.deleted())
                .map(RoleBinding::roleId)
                .filter(usable::containsKey)
                .distinct()
                .sorted()
                .toList();
    }
}
