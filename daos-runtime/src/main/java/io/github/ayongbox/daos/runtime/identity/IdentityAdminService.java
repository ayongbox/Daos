package io.github.ayongbox.daos.runtime.identity;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.auth.PasswordHasher;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.core.spi.AccountStore;
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
 * <p>角色绑定成功会写 GRANT 审计，主体为操作人，资源为角色。涉及登录密码的成员操作
 * 需要额外装配 {@link AccountStore} 与 {@link PasswordHasher}。</p>
 */
public final class IdentityAdminService {

    private final IdentityStore identityStore;
    private final AccountStore accountStore;
    private final PasswordHasher passwordHasher;
    private final AuditLog auditLog;
    private final Clock clock;

    public IdentityAdminService(IdentityStore identityStore) {
        this(identityStore, null, null, AuditLog.noop(), Clock.systemUTC());
    }

    public IdentityAdminService(IdentityStore identityStore, AuditLog auditLog, Clock clock) {
        this(identityStore, null, null, auditLog, clock);
    }

    public IdentityAdminService(
            IdentityStore identityStore,
            AccountStore accountStore,
            PasswordHasher passwordHasher,
            AuditLog auditLog,
            Clock clock) {
        this.identityStore = Objects.requireNonNull(identityStore, "identityStore");
        this.accountStore = accountStore;
        this.passwordHasher = passwordHasher;
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

    /**
     * 创建成员并同时建立登录账号，密码按登录同样的哈希方式保存。
     * 成员账号与登录账号共用 username，在租户内都不得重复。
     */
    public OrgMember createMember(
            String tenantId, String operatorId, String username, String displayName,
            String password, String deptId) {
        requireText(password, "password");
        AccountStore accounts = requireAccountStore();
        if (accounts.findByUsername(tenantId, username).isPresent()) {
            throw new IllegalArgumentException("成员账号已存在: " + username);
        }
        OrgMember member = createMember(tenantId, operatorId, username, displayName, deptId);
        accounts.save(new Account(
                Ids.ulid(), tenantId, username, displayName,
                requirePasswordHasher().hash(password), member.id(), true));
        return member;
    }

    /**
     * 更新部门。{@code name} / {@code parentId} / {@code sortOrder} 为 null 表示不修改；
     * {@code parentId} 传空字符串表示移到根层级。移动部门时同步重算子树的物化路径。
     */
    public OrgDepartment updateDepartment(
            String tenantId, String operatorId, String departmentId,
            String name, String parentId, Integer sortOrder) {
        requireText(tenantId, "tenantId");
        requireText(operatorId, "operatorId");
        requireText(departmentId, "departmentId");
        OrgDepartment current = findDepartment(tenantId, departmentId);

        String newParentId = current.parentId();
        String newPath = current.path();
        if (parentId != null) {
            String wantedParent = parentId.isBlank() ? null : parentId;
            if (!Objects.equals(wantedParent, current.parentId())) {
                if (wantedParent == null) {
                    newParentId = null;
                    newPath = "/" + departmentId + "/";
                } else {
                    if (wantedParent.equals(departmentId)) {
                        throw new IllegalArgumentException("部门不能以自身为父节点: " + departmentId);
                    }
                    OrgDepartment parent = activeDepartment(tenantId, wantedParent);
                    if (parent.path().startsWith(current.path())) {
                        throw new IllegalArgumentException("父部门不能是自身的子部门: " + wantedParent);
                    }
                    newParentId = parent.id();
                    newPath = parent.path() + departmentId + "/";
                }
            }
        }

        String now = now();
        OrgDepartment updated = new OrgDepartment(
                current.id(), current.tenantId(), newParentId, current.code(),
                name == null ? current.name() : name, newPath,
                sortOrder == null ? current.sort() : sortOrder,
                current.enabled(), current.ext(), current.createdBy(), current.createdAt(),
                operatorId, now, false, current.schemaVersion());
        identityStore.saveDepartment(updated);

        if (!newPath.equals(current.path())) {
            for (OrgDepartment descendant : identityStore.findDepartments(tenantId)) {
                if (!descendant.id().equals(departmentId)
                        && !descendant.deleted()
                        && descendant.path().startsWith(current.path())) {
                    String movedPath = newPath + descendant.path().substring(current.path().length());
                    identityStore.saveDepartment(new OrgDepartment(
                            descendant.id(), descendant.tenantId(), descendant.parentId(),
                            descendant.code(), descendant.name(), movedPath, descendant.sort(),
                            descendant.enabled(), descendant.ext(), descendant.createdBy(),
                            descendant.createdAt(), operatorId, now, false,
                            descendant.schemaVersion()));
                }
            }
        }
        return updated;
    }

    /**
     * 软删除部门。存在子部门或成员时拒绝，避免层级与归属悬空。
     */
    public void deleteDepartment(String tenantId, String operatorId, String departmentId) {
        requireText(tenantId, "tenantId");
        requireText(operatorId, "operatorId");
        requireText(departmentId, "departmentId");
        OrgDepartment current = findDepartment(tenantId, departmentId);

        boolean hasChildren = identityStore.findDepartments(tenantId).stream()
                .filter(department -> !department.deleted())
                .anyMatch(department -> departmentId.equals(department.parentId()));
        if (hasChildren) {
            throw new IllegalArgumentException("部门下存在子部门，无法删除: " + current.code());
        }
        boolean hasMembers = identityStore.findMembers(tenantId).stream()
                .filter(member -> !member.deleted())
                .anyMatch(member -> departmentId.equals(member.deptId()));
        if (hasMembers) {
            throw new IllegalArgumentException("部门下存在成员，无法删除: " + current.code());
        }

        String now = now();
        identityStore.saveDepartment(new OrgDepartment(
                current.id(), current.tenantId(), current.parentId(), current.code(),
                current.name(), current.path(), current.sort(), current.enabled(), current.ext(),
                current.createdBy(), current.createdAt(), operatorId, now, true,
                current.schemaVersion()));
    }

    /**
     * 更新成员。{@code displayName} / {@code deptId} / {@code status} 为 null 表示不修改，
     * {@code deptId} 传空字符串表示移出部门；{@code password} 非空时重置登录账号密码，
     * 账号不存在时按 username 补建。
     */
    public OrgMember updateMember(
            String tenantId, String operatorId, String memberId,
            String displayName, String deptId, MemberStatus status, String password) {
        requireText(tenantId, "tenantId");
        requireText(operatorId, "operatorId");
        requireText(memberId, "memberId");
        OrgMember current = findMember(tenantId, memberId);

        String newDeptId = current.deptId();
        if (deptId != null) {
            newDeptId = deptId.isBlank() ? null : deptId;
            if (newDeptId != null && !newDeptId.equals(current.deptId())) {
                activeDepartment(tenantId, newDeptId);
            }
        }

        String now = now();
        OrgMember updated = new OrgMember(
                current.id(), current.tenantId(), current.username(),
                displayName == null ? current.displayName() : displayName,
                current.email(), current.phone(), newDeptId,
                status == null ? current.status() : status,
                current.ext(), current.createdBy(), current.createdAt(), operatorId, now,
                false, current.schemaVersion());
        identityStore.saveMember(updated);

        if (password != null) {
            requireText(password, "password");
            resetAccountPassword(tenantId, updated, password);
        }
        return updated;
    }

    private void resetAccountPassword(String tenantId, OrgMember member, String password) {
        AccountStore accounts = requireAccountStore();
        String passwordHash = requirePasswordHasher().hash(password);
        Account existing = accounts.findByUsername(tenantId, member.username()).orElse(null);
        if (existing == null) {
            accounts.save(new Account(
                    Ids.ulid(), tenantId, member.username(), member.displayName(),
                    passwordHash, member.id(), true));
            return;
        }
        accounts.save(new Account(
                existing.id(), existing.tenantId(), existing.username(), existing.displayName(),
                passwordHash, existing.memberId(), existing.enabled()));
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

    private OrgDepartment findDepartment(String tenantId, String departmentId) {
        return identityStore.findDepartments(tenantId).stream()
                .filter(candidate -> candidate.id().equals(departmentId))
                .filter(candidate -> !candidate.deleted())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("部门不存在: " + departmentId));
    }

    private OrgMember findMember(String tenantId, String memberId) {
        return identityStore.findMembers(tenantId).stream()
                .filter(candidate -> candidate.id().equals(memberId))
                .filter(candidate -> !candidate.deleted())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("成员不存在: " + memberId));
    }

    private AccountStore requireAccountStore() {
        if (accountStore == null) {
            throw new IllegalStateException("未装配账号仓储，无法管理登录账号");
        }
        return accountStore;
    }

    private PasswordHasher requirePasswordHasher() {
        if (passwordHasher == null) {
            throw new IllegalStateException("未装配密码哈希器，无法管理登录密码");
        }
        return passwordHasher;
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
