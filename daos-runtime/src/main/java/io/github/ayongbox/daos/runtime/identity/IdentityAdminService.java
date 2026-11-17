package io.github.ayongbox.daos.runtime.identity;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.core.spi.IdentityStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 授权管理。组织、成员、角色与绑定的写入入口，所有变更按租户隔离并校验引用完整性。
 *
 * <p>角色绑定成功会写 GRANT 审计，主体为操作人，资源为角色。</p>
 */
public final class IdentityAdminService {

    private final IdentityStore identityStore;
    private final AuditLog auditLog;
    private final Clock clock;

    public IdentityAdminService(IdentityStore identityStore) {
        this(identityStore, AuditLog.noop(), Clock.systemUTC());
    }

    public IdentityAdminService(IdentityStore identityStore, AuditLog auditLog, Clock clock) {
        this.identityStore = Objects.requireNonNull(identityStore, "identityStore");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OrgDepartment createDepartment(
            String tenantId, String operatorId, String parentId, String code, String name) {
        requireText(tenantId, "tenantId");
        requireText(operatorId, "operatorId");
        requireText(code, "code");
        requireText(name, "name");

        Optional<OrgDepartment> duplicated = identityStore.findDepartments(tenantId).stream()
                .filter(department -> !department.deleted())
                .filter(department -> department.code().equals(code))
                .findFirst();
        if (duplicated.isPresent()) {
            throw new IllegalArgumentException("部门编码已存在: " + code);
        }

        String id = Ids.ulid();
        String path = null;
        if (parentId != null && !parentId.isBlank()) {
            OrgDepartment parent = activeDepartment(tenantId, parentId);
            path = parent.path() + id + "/";
        }

        String now = now();
        OrgDepartment department = new OrgDepartment(
                id, tenantId, parentId, code, name, path, 0, true, Map.of(),
                operatorId, now, operatorId, now, false, 1);
        identityStore.saveDepartment(department);
        return department;
    }

    public OrgMember createMember(
            String tenantId, String operatorId, String username, String displayName, String deptId) {
        requireText(tenantId, "tenantId");
        requireText(operatorId, "operatorId");
        requireText(username, "username");
        requireText(displayName, "displayName");

        boolean duplicated = identityStore.findMembers(tenantId).stream()
                .filter(member -> !member.deleted())
                .anyMatch(member -> member.username().equals(username));
        if (duplicated) {
            throw new IllegalArgumentException("成员账号已存在: " + username);
        }
        if (deptId != null && !deptId.isBlank()) {
            activeDepartment(tenantId, deptId);
        }

        String now = now();
        OrgMember member = new OrgMember(
                Ids.ulid(), tenantId, username, displayName, null, null, deptId,
                MemberStatus.ACTIVE, Map.of(), operatorId, now, operatorId, now, false, 1);
        identityStore.saveMember(member);
        return member;
    }

    public RoleDefinition createRole(
            String tenantId, String operatorId, String appId, String code, String name, RoleScope scope) {
        requireText(tenantId, "tenantId");
        requireText(operatorId, "operatorId");
        requireText(code, "code");
        requireText(name, "name");
        Objects.requireNonNull(scope, "scope");

        String normalizedAppId = scope == RoleScope.PLATFORM ? null : appId;
        boolean duplicated = identityStore.findRoles(tenantId).stream()
                .filter(role -> !role.deleted())
                .anyMatch(role -> role.code().equals(code)
                        && Objects.equals(role.appId(), normalizedAppId));
        if (duplicated) {
            throw new IllegalArgumentException("角色编码已存在: " + code);
        }

        String now = now();
        RoleDefinition role = new RoleDefinition(
                Ids.ulid(), tenantId, normalizedAppId, code, name, scope, null, true, Map.of(),
                operatorId, now, operatorId, now, false, 1);
        identityStore.saveRole(role);
        return role;
    }

    public RoleBinding bindRole(String tenantId, String operatorId, String memberId, String roleId) {
        requireText(tenantId, "tenantId");
        requireText(operatorId, "operatorId");
        requireText(memberId, "memberId");
        requireText(roleId, "roleId");

        OrgMember member = identityStore.findMember(tenantId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("成员不存在: " + memberId));
        if (!member.active()) {
            throw new IllegalArgumentException("成员已停用或删除: " + memberId);
        }

        RoleDefinition role = identityStore.findRoles(tenantId).stream()
                .filter(candidate -> candidate.id().equals(roleId))
                .filter(candidate -> !candidate.deleted())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleId));
        if (!role.enabled()) {
            throw new IllegalArgumentException("角色已停用: " + roleId);
        }

        boolean duplicated = identityStore.findBindings(tenantId, memberId).stream()
                .filter(binding -> !binding.deleted())
                .anyMatch(binding -> binding.roleId().equals(roleId));
        if (duplicated) {
            throw new IllegalArgumentException("成员已持有角色: " + roleId);
        }

        String now = now();
        RoleBinding binding = new RoleBinding(
                Ids.ulid(), tenantId, roleId, memberId, Map.of(),
                operatorId, now, operatorId, now, false, 1);
        identityStore.saveBinding(binding);

        auditLog.record(new AuditEvent(
                Ids.ulid(), tenantId, role.appId(), operatorId, AuditAction.GRANT, null, roleId,
                AuditOutcome.SUCCESS, null, null, null, null, Instant.now(clock),
                Map.of("memberId", memberId, "bindId", binding.id())));
        return binding;
    }

    private OrgDepartment activeDepartment(String tenantId, String departmentId) {
        OrgDepartment department = identityStore.findDepartments(tenantId).stream()
                .filter(candidate -> candidate.id().equals(departmentId))
                .filter(candidate -> !candidate.deleted())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("部门不存在: " + departmentId));
        if (!department.enabled()) {
            throw new IllegalArgumentException("部门已停用: " + departmentId);
        }
        return department;
    }

    private String now() {
        return Instant.now(clock).toString();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
