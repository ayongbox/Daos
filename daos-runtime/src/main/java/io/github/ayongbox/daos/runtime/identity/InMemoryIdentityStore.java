package io.github.ayongbox.daos.runtime.identity;

import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.spi.IdentityStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存身份仓储。用于测试与单机场景，生产实现应落库并按租户加索引。
 */
public final class InMemoryIdentityStore implements IdentityStore {

    private final Map<String, OrgDepartment> departments = new ConcurrentHashMap<>();
    private final Map<String, OrgMember> members = new ConcurrentHashMap<>();
    private final Map<String, RoleDefinition> roles = new ConcurrentHashMap<>();
    private final Map<String, RoleBinding> bindings = new ConcurrentHashMap<>();

    public InMemoryIdentityStore(
            List<OrgDepartment> departments,
            List<OrgMember> members,
            List<RoleDefinition> roles,
            List<RoleBinding> bindings) {
        if (departments != null) {
            departments.forEach(this::saveDepartment);
        }
        if (members != null) {
            members.forEach(this::saveMember);
        }
        if (roles != null) {
            roles.forEach(this::saveRole);
        }
        if (bindings != null) {
            bindings.forEach(this::saveBinding);
        }
    }

    @Override
    public Optional<OrgMember> findMember(String tenantId, String memberId) {
        return Optional.ofNullable(members.get(key(tenantId, memberId)));
    }

    @Override
    public List<OrgMember> findMembers(String tenantId) {
        return members.values().stream()
                .filter(member -> member.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<OrgDepartment> findDepartments(String tenantId) {
        return departments.values().stream()
                .filter(department -> department.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<RoleDefinition> findRoles(String tenantId) {
        return roles.values().stream()
                .filter(role -> role.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<RoleBinding> findBindings(String tenantId, String memberId) {
        return bindings.values().stream()
                .filter(binding -> binding.tenantId().equals(tenantId))
                .filter(binding -> binding.memberId().equals(memberId))
                .toList();
    }

    @Override
    public void saveDepartment(OrgDepartment department) {
        departments.put(key(department.tenantId(), department.id()), department);
    }

    @Override
    public void saveMember(OrgMember member) {
        members.put(key(member.tenantId(), member.id()), member);
    }

    @Override
    public void saveRole(RoleDefinition role) {
        roles.put(key(role.tenantId(), role.id()), role);
    }

    @Override
    public void saveBinding(RoleBinding binding) {
        bindings.put(key(binding.tenantId(), binding.id()), binding);
    }

    private static String key(String tenantId, String id) {
        return tenantId + "\u0000" + id;
    }
}
