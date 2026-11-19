package io.github.ayongbox.daos.web.admin;

import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.metadata.identity.OrgDepartment;
import io.github.ayongbox.daos.runtime.identity.IdentityAdminQueryService;
import io.github.ayongbox.daos.runtime.identity.IdentityAdminService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static io.github.ayongbox.daos.web.admin.AdminSessions.operator;
import static io.github.ayongbox.daos.web.admin.AdminSessions.session;

/**
 * 管理后台的部门管理接口。组织数据是租户级的，只要求登录，不做应用级权限校验。
 */
@RestController
@RequestMapping("/api/admin/departments")
public class AdminDepartmentController {

    private final IdentityAdminService identityAdminService;
    private final IdentityAdminQueryService identityAdminQueryService;

    public AdminDepartmentController(
            IdentityAdminService identityAdminService,
            IdentityAdminQueryService identityAdminQueryService) {
        this.identityAdminService = Objects.requireNonNull(identityAdminService);
        this.identityAdminQueryService = Objects.requireNonNull(identityAdminQueryService);
    }

    @GetMapping
    public R<List<DepartmentView>> list(HttpServletRequest request) {
        Session session = session(request);
        return R.ok(identityAdminQueryService.listDepartments(session.tenantId()).stream()
                .map(DepartmentView::of)
                .toList());
    }

    @PostMapping
    public R<DepartmentView> create(
            @RequestBody CreateDepartmentRequest body, HttpServletRequest request) {
        Session session = session(request);
        OrgDepartment department = identityAdminService.createDepartment(
                session.tenantId(), operator(session), body.parentId(), body.code(), body.name());
        return R.ok(DepartmentView.of(department));
    }

    @PutMapping("/{id}")
    public R<DepartmentView> update(
            @PathVariable String id,
            @RequestBody UpdateDepartmentRequest body,
            HttpServletRequest request) {
        Session session = session(request);
        OrgDepartment department = identityAdminService.updateDepartment(
                session.tenantId(), operator(session), id,
                body.name(), body.parentId(), body.sortOrder());
        return R.ok(DepartmentView.of(department));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id, HttpServletRequest request) {
        Session session = session(request);
        identityAdminService.deleteDepartment(session.tenantId(), operator(session), id);
        return R.ok(null);
    }

    public record CreateDepartmentRequest(String parentId, String code, String name) {
    }

    /**
     * name / parentId / sortOrder 缺省表示不修改；parentId 传空字符串表示移到根层级。
     */
    public record UpdateDepartmentRequest(String name, String parentId, Integer sortOrder) {
    }

    public record DepartmentView(
            String id,
            String parentId,
            String code,
            String name,
            String path,
            Integer sortOrder,
            boolean deleted) {

        static DepartmentView of(OrgDepartment department) {
            return new DepartmentView(
                    department.id(), department.parentId(), department.code(), department.name(),
                    department.path(), department.sort(), department.deleted());
        }
    }
}
