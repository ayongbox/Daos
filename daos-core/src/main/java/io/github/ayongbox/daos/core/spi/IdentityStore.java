package io.github.ayongbox.daos.core.spi;

import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 身份仓储。组织、成员与角色属于租户级数据，不随应用发布。
 *
 * <p>实现必须按租户隔离；返回的集合不可变，已删除数据可以选择返回或过滤，
 * 调用方仍会再次校验 {@code deleted}。</p>
 */
public interface IdentityStore {

    Optional<OrgMember> findMember(String tenantId, String memberId);

    List<OrgMember> findMembers(String tenantId);

    List<OrgDepartment> findDepartments(String tenantId);

    List<RoleDefinition> findRoles(String tenantId);

    List<RoleBinding> findBindings(String tenantId, String memberId);

    void saveDepartment(OrgDepartment department);

    void saveMember(OrgMember member);

    void saveRole(RoleDefinition role);

    void saveBinding(RoleBinding binding);
}
