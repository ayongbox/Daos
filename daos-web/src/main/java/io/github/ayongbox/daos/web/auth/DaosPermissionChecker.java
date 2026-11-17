package io.github.ayongbox.daos.web.auth;

import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.runtime.permission.PermissionRuleMatcher;
import io.github.ayongbox.daos.runtime.permission.PermissionRuleMatcher.SubjectMatch;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 把 Baseon 的全局权限串（{@code "table:read"}）适配到 Daos 的应用级权限上下文。
 *
 * <p>两参方法按应用维度裁决：从当前请求取登录会话，按需解析权限上下文并缓存到
 * {@link PermissionContextHolder}。权限语义仍由 Daos 引擎裁决：拒绝优先、默认拒绝；
 * 无法确定应用、会话或上下文时一律拒绝。</p>
 */
public final class DaosPermissionChecker implements PermissionChecker {

    private final SessionPermissionService permissionService;
    private final Clock clock;

    public DaosPermissionChecker(SessionPermissionService permissionService) {
        this(permissionService, Clock.systemUTC());
    }

    public DaosPermissionChecker(SessionPermissionService permissionService, Clock clock) {
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 无应用维度的校验：只对已解析的当前请求上下文生效。
     */
    @Override
    public boolean hasPermission(String permission) {
        PermissionContext context = currentContext(null);
        return context != null && allowed(context, permission);
    }

    @Override
    public boolean hasPermission(String appId, String permission) {
        if (appId == null || appId.isBlank()) {
            return false;
        }
        PermissionContext context = currentContext(appId);
        return context != null && allowed(context, permission);
    }

    private boolean allowed(PermissionContext context, String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        String[] parts = permission.split(":");
        if (parts.length != 2) {
            return false;
        }
        ResourceType resourceType = parse(ResourceType.class, parts[0]);
        ActionType action = parse(ActionType.class, parts[1]);
        if (resourceType == null || action == null) {
            return false;
        }

        Instant now = clock.instant();
        boolean allowed = false;
        for (SchemaPermission rule : context.permissions()) {
            if (!rule.enabled() || rule.deleted()
                    || rule.resource().type() != resourceType
                    || !rule.actions().contains(action)
                    || !PermissionRuleMatcher.active(rule, now)) {
                continue;
            }
            SubjectMatch match = PermissionRuleMatcher.matchSubject(rule.subject(), context);
            if (match == SubjectMatch.NO_MATCH) {
                continue;
            }
            if (match == SubjectMatch.UNKNOWN) {
                if (rule.effect() == EffectType.DENY) {
                    return false;
                }
                continue;
            }
            if (rule.effect() == EffectType.DENY) {
                return false;
            }
            allowed = true;
        }
        return allowed;
    }

    /**
     * 取当前请求的权限上下文：已缓存则复用，未缓存时从会话按应用解析。
     *
     * @param appId 为 null 时只读取缓存，不触发解析
     */
    private PermissionContext currentContext(String appId) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        if (appId == null) {
            return PermissionContextHolder.get(request);
        }
        PermissionContext cached = PermissionContextHolder.get(request, appId);
        if (cached != null) {
            return cached;
        }
        Session session = (Session) request.getAttribute(SessionAuthInterceptor.SESSION_ATTRIBUTE);
        if (session == null) {
            return null;
        }
        PermissionContext resolved;
        try {
            resolved = permissionService.resolve(session, appId);
        } catch (AccessDeniedException | IllegalStateException denied) {
            return null;
        }
        PermissionContextHolder.put(request, appId, resolved);
        return resolved;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        return servlet.getRequest();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
