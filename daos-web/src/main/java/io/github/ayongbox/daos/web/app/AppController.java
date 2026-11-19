package io.github.ayongbox.daos.web.app;

import io.github.ayongbox.baseon.core.context.SecurityUtil;
import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.runtime.app.AppService;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应用列表与创建。创建应用不接受未登录请求，也不依赖应用级权限（此时应用尚不存在）。
 */
@RestController
@RequestMapping("/api/apps")
public class AppController {

    private final AppService appService;
    private final PermissionChecker permissionChecker;

    public AppController(AppService appService, PermissionChecker permissionChecker) {
        this.appService = appService;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping
    public R<List<AppSummary>> list() {
        return R.ok(appService.listDrafts().stream()
                .map(app -> AppSummary.of(app, appService.releaseInfo(app)))
                .toList());
    }

    @PostMapping
    public R<AppSummary> create(@RequestBody CreateAppRequest body, HttpServletRequest request) {
        Session session = session(request);
        SchemaApp app = appService.create(
                session.tenantId(), session.memberId(), session.username(),
                body.code(), body.displayName(), body.description(), body.icon());
        return R.ok(AppSummary.of(app, appService.releaseInfo(app)));
    }

    @DeleteMapping("/{appId}")
    public R<Void> delete(@PathVariable String appId) {
        if (!permissionChecker.hasPermission(appId, "app:delete")) {
            throw new AccessDeniedException("无权限执行 app:delete");
        }
        String operator = SecurityUtil.getUsername();
        appService.delete(appId, operator == null || operator.isBlank() ? "system" : operator);
        return R.ok(null);
    }

    private static Session session(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthInterceptor.SESSION_ATTRIBUTE);
        if (value instanceof Session session) {
            return session;
        }
        throw new IllegalStateException("未登录或会话已过期");
    }

    /**
     * 应用编码对普通用户不可见，可省略；省略时由服务端生成。
     */
    public record CreateAppRequest(String code, String displayName, String description, String icon) {
    }

    public record AppSummary(
            String id,
            String code,
            String displayName,
            String description,
            String icon,
            String status,
            long activeReleaseVersion,
            boolean changedSinceRelease,
            int tableCount,
            String updatedAt) {

        static AppSummary of(SchemaApp app, AppService.ReleaseInfo release) {
            return new AppSummary(
                    app.id(), app.code(), app.displayName(), app.description(), app.icon(),
                    app.status().name(), release.activeReleaseVersion(),
                    release.changedSinceRelease(), app.tables().size(), app.updatedAt());
        }
    }
}
