package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.spi.IdentityStore;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 身份仓储的数据库实现。部门、成员、角色、绑定各一张表，按租户隔离。
 */
public final class JdbcIdentityStore implements IdentityStore {

    private final JdbcTemplate jdbc;
    private final JsonCodec codec;

    public JdbcIdentityStore(JdbcTemplate jdbc, JsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public Optional<OrgMember> findMember(String tenantId, String memberId) {
        return payloads("SELECT payload FROM daos_member WHERE tenant_id = ? AND member_id = ?",
                tenantId, memberId)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, OrgMember.class));
    }

    @Override
    public List<OrgMember> findMembers(String tenantId) {
        return payloads("SELECT payload FROM daos_member WHERE tenant_id = ?", tenantId).stream()
                .map(payload -> codec.read(payload, OrgMember.class))
                .toList();
    }

    @Override
    public List<OrgDepartment> findDepartments(String tenantId) {
        return payloads("SELECT payload FROM daos_department WHERE tenant_id = ?", tenantId).stream()
                .map(payload -> codec.read(payload, OrgDepartment.class))
                .toList();
    }

    @Override
    public List<RoleDefinition> findRoles(String tenantId) {
        return payloads("SELECT payload FROM daos_role WHERE tenant_id = ?", tenantId).stream()
                .map(payload -> codec.read(payload, RoleDefinition.class))
                .toList();
    }

    @Override
    public List<RoleBinding> findBindings(String tenantId, String memberId) {
        return payloads(
                "SELECT payload FROM daos_role_binding WHERE tenant_id = ? AND member_id = ?",
                tenantId, memberId).stream()
                .map(payload -> codec.read(payload, RoleBinding.class))
                .toList();
    }

    @Override
    public void saveDepartment(OrgDepartment department) {
        int updated = jdbc.update(
                "UPDATE daos_department SET payload = ? WHERE tenant_id = ? AND department_id = ?",
                codec.write(department), department.tenantId(), department.id());
        if (updated == 0) {
            insert("INSERT INTO daos_department (tenant_id, department_id, payload) VALUES (?, ?, ?)",
                    department.tenantId(), department.id(), codec.write(department));
        }
    }

    @Override
    public void saveMember(OrgMember member) {
        int updated = jdbc.update(
                "UPDATE daos_member SET payload = ? WHERE tenant_id = ? AND member_id = ?",
                codec.write(member), member.tenantId(), member.id());
        if (updated == 0) {
            insert("INSERT INTO daos_member (tenant_id, member_id, payload) VALUES (?, ?, ?)",
                    member.tenantId(), member.id(), codec.write(member));
        }
    }

    @Override
    public void saveRole(RoleDefinition role) {
        int updated = jdbc.update(
                "UPDATE daos_role SET payload = ? WHERE tenant_id = ? AND role_id = ?",
                codec.write(role), role.tenantId(), role.id());
        if (updated == 0) {
            insert("INSERT INTO daos_role (tenant_id, role_id, payload) VALUES (?, ?, ?)",
                    role.tenantId(), role.id(), codec.write(role));
        }
    }

    @Override
    public void saveBinding(RoleBinding binding) {
        int updated = jdbc.update(
                "UPDATE daos_role_binding SET member_id = ?, payload = ?"
                        + " WHERE tenant_id = ? AND binding_id = ?",
                binding.memberId(), codec.write(binding), binding.tenantId(), binding.id());
        if (updated == 0) {
            insert("INSERT INTO daos_role_binding (tenant_id, binding_id, member_id, payload)"
                            + " VALUES (?, ?, ?, ?)",
                    binding.tenantId(), binding.id(), binding.memberId(), codec.write(binding));
        }
    }

    private void insert(String sql, Object... args) {
        try {
            jdbc.update(sql, args);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("身份数据写入冲突", e);
        }
    }

    private List<String> payloads(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }
}
