package io.github.ayongbox.daos.web.form;

import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.runtime.auth.AuthenticationException;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.runtime.subform.SubformService;
import io.github.ayongbox.daos.web.auth.PermissionContextHolder;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 表单记录接口。支持主表与子表单一次提交、完整回显与级联删除。
 *
 * <p>主体与租户始终来自登录会话；主表与子表分别走各自的权限裁决，
 * 任一子表无权时整体事务回滚。</p>
 */
@RestController
@RequestMapping("/api/apps/{appId}/forms/{formCode}/records")
public class FormRecordController {

    private final SubformService subformService;
    private final SessionPermissionService permissionService;
    private final PermissionChecker permissionChecker;

    public FormRecordController(
            SubformService subformService,
            SessionPermissionService permissionService,
            PermissionChecker permissionChecker) {
        this.subformService = subformService;
        this.permissionService = permissionService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public R<Map<String, Object>> create(
            @PathVariable String appId,
            @PathVariable String formCode,
            @RequestBody FormRecordRequest body,
            HttpServletRequest servletRequest) {
        requirePermission(appId, "create");
        Map<String, Object> values = body == null || body.values() == null
                ? Map.of() : body.values();
        Map<String, List<Map<String, Object>>> subforms = body == null || body.subforms() == null
                ? Map.of() : body.subforms();
        String id = subformService.create(
                appId, formCode, values, subforms, context(servletRequest, appId));
        return R.ok(Map.of("id", id));
    }

    @GetMapping("/{recordId}")
    public R<SubformService.FormRecord> get(
            @PathVariable String appId,
            @PathVariable String formCode,
            @PathVariable String recordId,
            HttpServletRequest servletRequest) {
        requirePermission(appId, "read");
        return R.ok(subformService.load(
                appId, formCode, recordId, context(servletRequest, appId)));
    }

    @PutMapping("/{recordId}")
    public R<Map<String, Object>> update(
            @PathVariable String appId,
            @PathVariable String formCode,
            @PathVariable String recordId,
            @RequestParam long version,
            @RequestBody FormRecordRequest body,
            HttpServletRequest servletRequest) {
        requirePermission(appId, "update");
        Map<String, Object> values = body == null || body.values() == null
                ? Map.of() : body.values();
        Map<String, List<Map<String, Object>>> subforms = body == null || body.subforms() == null
                ? Map.of() : body.subforms();
        int affected = subformService.update(
                appId, formCode, recordId, version, values, subforms,
                context(servletRequest, appId));
        return R.ok(Map.of("affected", affected));
    }

    @DeleteMapping("/{recordId}")
    public R<Map<String, Object>> delete(
            @PathVariable String appId,
            @PathVariable String formCode,
            @PathVariable String recordId,
            @RequestParam long version,
            HttpServletRequest servletRequest) {
        requirePermission(appId, "delete");
        int affected = subformService.delete(
                appId, formCode, recordId, version, context(servletRequest, appId));
        return R.ok(Map.of("affected", affected));
    }

    private PermissionContext context(HttpServletRequest request, String appId) {
        PermissionContext cached = PermissionContextHolder.get(request, appId);
        if (cached != null) {
            return cached;
        }
        Session session = (Session) request.getAttribute(SessionAuthInterceptor.SESSION_ATTRIBUTE);
        if (session == null) {
            throw new AuthenticationException("未登录或会话已过期");
        }
        PermissionContext context = permissionService.resolve(session, appId);
        PermissionContextHolder.put(request, appId, context);
        return context;
    }

    private void requirePermission(String appId, String action) {
        if (!permissionChecker.hasPermission(appId, "table:" + action)) {
            throw new AccessDeniedException("无权限执行 table:" + action);
        }
    }

    /**
     * 提交载荷：主表字段与按子表单表单项 id 分组的子表行。
     */
    public record FormRecordRequest(
            Map<String, Object> values,
            Map<String, List<Map<String, Object>>> subforms) {
    }
}
