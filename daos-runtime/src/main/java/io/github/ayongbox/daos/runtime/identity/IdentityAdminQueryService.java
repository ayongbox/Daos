package io.github.ayongbox.daos.runtime.identity;

import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.spi.IdentityStore;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 授权查询。管理端列表读取入口，只读不写；成员列表附带所属部门名称便于展示。
 */
public final class IdentityAdminQueryService {

    private final IdentityStore identityStore;

    public IdentityAdminQueryService(IdentityStore identityStore) {
        this.identityStore = Objects.requireNonNull(identityStore, "identityStore");
    }

    /**
     * 返回未删除的部门，按 sortOrder、code 排序。
     */
    public List<OrgDepartment> listDepartments(String tenantId) {
        requireText(tenantId, "tenantId");
        return identityStore.findDepartments(tenantId).stream()
                .filter(department -> !department.deleted())
                .sorted(Comparator.comparingInt(OrgDepartment::sort)
                        .thenComparing(OrgDepartment::code))
                .toList();
    }

    /**
     * 返回未删除的成员，按 username 排序；部门名称按 id 解析，部门已删除时仍返回其名称。
     */
    public List<MemberRow> listMembers(String tenantId) {
        requireText(tenantId, "tenantId");
        Map<String, String> departmentNames = new HashMap<>();
        for (OrgDepartment department : identityStore.findDepartments(tenantId)) {
            departmentNames.put(department.id(), department.name());
        }
        return identityStore.findMembers(tenantId).stream()
                .filter(member -> !member.deleted())
                .sorted(Comparator.comparing(OrgMember::username))
                .map(member -> new MemberRow(member, departmentNames.get(member.deptId())))
                .toList();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    /**
     * 成员行，携带所属部门名称。
     */
    public record MemberRow(OrgMember member, String deptName) {
    }
}
