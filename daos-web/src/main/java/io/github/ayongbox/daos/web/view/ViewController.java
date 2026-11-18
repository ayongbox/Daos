package io.github.ayongbox.daos.web.view;

import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.runtime.auth.AuthenticationException;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.runtime.view.ViewService;
import io.github.ayongbox.daos.runtime.view.ViewService.ViewAggregatePage;
import io.github.ayongbox.daos.runtime.view.ViewService.ViewDetail;
import io.github.ayongbox.daos.runtime.view.ViewService.ViewPage;
import io.github.ayongbox.daos.runtime.view.ViewService.ViewSummary;
import io.github.ayongbox.daos.web.auth.PermissionContextHolder;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 视图运行时接口。只读取生效快照，草稿中的视图配置发布后才可见。
 */
@RestController
@RequestMapping("/api/apps/{appId}/views")
public class ViewController {

    private static final String VIEW_PERMISSION = "app:view";
    private static final String READ_PERMISSION = "table:read";

    private final ViewService viewService;
    private final SessionPermissionService permissionService;
    private final PermissionChecker permissionChecker;

    public ViewController(
            ViewService viewService,
            SessionPermissionService permissionService,
            PermissionChecker permissionChecker) {
        this.viewService = viewService;
        this.permissionService = permissionService;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping
    public R<List<ViewSummary>> list(@PathVariable String appId) {
        require(appId, VIEW_PERMISSION);
        return R.ok(viewService.listViews(appId));
    }

    @GetMapping("/{viewCode}")
    public R<ViewDetail> render(@PathVariable String appId, @PathVariable String viewCode) {
        require(appId, VIEW_PERMISSION);
        return R.ok(viewService.render(appId, viewCode));
    }

    @GetMapping("/{viewCode}/records")
    public R<ViewPage> records(
            @PathVariable String appId,
            @PathVariable String viewCode,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "0") long size,
            HttpServletRequest servletRequest) {
        require(appId, READ_PERMISSION);
        return R.ok(viewService.query(appId, viewCode, current, size, context(servletRequest, appId)));
    }

    @GetMapping("/{viewCode}/aggregate")
    public R<ViewAggregatePage> aggregate(
            @PathVariable String appId,
            @PathVariable String viewCode,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "0") long size,
            HttpServletRequest servletRequest) {
        require(appId, READ_PERMISSION);
        return R.ok(viewService.aggregate(
                appId, viewCode, current, size, context(servletRequest, appId)));
    }

    private void require(String appId, String action) {
        if (!permissionChecker.hasPermission(appId, action)) {
            throw new AccessDeniedException("无权限执行 " + action);
        }
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
}
