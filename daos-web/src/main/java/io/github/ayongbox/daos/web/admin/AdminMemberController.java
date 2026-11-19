package io.github.ayongbox.daos.web.admin;

import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.runtime.identity.IdentityAdminQueryService;
import io.github.ayongbox.daos.runtime.identity.IdentityAdminService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static io.github.ayongbox.daos.web.admin.AdminSessions.operator;
import static io.github.ayongbox.daos.web.admin.AdminSessions.session;

/**
 * 管理后台的成员管理接口。只要求登录；创建与改密会同步维护登录账号。
 */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final IdentityAdminService identityAdminService;
    private final IdentityAdminQueryService identityAdminQueryService;

    public AdminMemberController(
            IdentityAdminService identityAdminService,
            IdentityAdminQueryService identityAdminQueryService) {
        this.identityAdminService = Objects.requireNonNull(identityAdminService);
        this.identityAdminQueryService = Objects.requireNonNull(identityAdminQueryService);
    }

    @GetMapping
    public R<List<MemberView>> list(HttpServletRequest request) {
        Session session = session(request);
        return R.ok(identityAdminQueryService.listMembers(session.tenantId()).stream()
                .map(MemberView::of)
                .toList());
    }

    @PostMapping
    public R<MemberView> create(@RequestBody CreateMemberRequest body, HttpServletRequest request) {
        Session session = session(request);
        OrgMember member = identityAdminService.createMember(
                session.tenantId(), operator(session), body.username(), body.displayName(),
                body.password(), body.deptId());
        return R.ok(MemberView.of(
                new IdentityAdminQueryService.MemberRow(member, deptName(session, member.deptId()))));
    }

    @PutMapping("/{id}")
    public R<MemberView> update(
            @PathVariable String id,
            @RequestBody UpdateMemberRequest body,
            HttpServletRequest request) {
        Session session = session(request);
        OrgMember member = identityAdminService.updateMember(
                session.tenantId(), operator(session), id, body.displayName(), body.deptId(),
                parseStatus(body.status()), body.password());
        return R.ok(MemberView.of(
                new IdentityAdminQueryService.MemberRow(member, deptName(session, member.deptId()))));
    }

    private String deptName(Session session, String deptId) {
        if (deptId == null) {
            return null;
        }
        return identityAdminQueryService.listDepartments(session.tenantId()).stream()
                .filter(department -> deptId.equals(department.id()))
                .findFirst()
                .map(OrgDepartment::name)
                .orElse(null);
    }

    private static MemberStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MemberStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("成员状态不合法: " + value + "，要求 ACTIVE/DISABLED");
        }
    }

    public record CreateMemberRequest(
            String username, String displayName, String password, String deptId) {
    }

    /**
     * displayName / deptId / status / password 缺省表示不修改；deptId 传空字符串表示移出部门；
     * password 出现时重置登录账号密码。
     */
    public record UpdateMemberRequest(
            String displayName, String deptId, String status, String password) {
    }

    public record MemberView(
            String id,
            String username,
            String displayName,
            String deptId,
            String deptName,
            String status,
            String createdAt) {

        static MemberView of(IdentityAdminQueryService.MemberRow row) {
            OrgMember member = row.member();
            return new MemberView(
                    member.id(), member.username(), member.displayName(), member.deptId(),
                    row.deptName(), member.status().name(), member.createdAt());
        }
    }
}
