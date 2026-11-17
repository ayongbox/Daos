package io.github.ayongbox.daos.runtime.identity;

import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.spi.IdentityStore;

import java.util.List;
import java.util.Optional;

/**
 * 内存身份仓储。用于测试与单机场景，生产实现应落库并按租户加索引。
 */
public final class InMemoryIdentityStore implements IdentityStore {

    private final List<OrgDepartment> departments;
    private final List<OrgMember> members;
    private final List<RoleDefinition> roles;
    private final List<RoleBinding> bindings;

    public InMemoryIdentityStore(
            List<OrgDepartment> departments,
            List<OrgMember> members,
            List<RoleDefinition> roles,
            List<RoleBinding> bindings) {
        this.departments = departments == null ? List.of() : List.copyOf(departments);
        this.members = members == null ? List.of() : List.copyOf(members);
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }

    @Override
    public Optional<OrgMember> findMember(String tenantId, String memberId) {
        return members.stream()
                .filter(member -> member.tenantId().equals(tenantId))
                .filter(member -> member.id().equals(memberId))
                .findFirst();
    }

    @Override
    public List<OrgDepartment> findDepartments(String tenantId) {
        return departments.stream()
                .filter(department -> department.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<RoleDefinition> findRoles(String tenantId) {
        return roles.stream()
                .filter(role -> role.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<RoleBinding> findBindings(String tenantId, String memberId) {
        return bindings.stream()
                .filter(binding -> binding.tenantId().equals(tenantId))
                .filter(binding -> binding.memberId().equals(memberId))
                .toList();
    }
}
