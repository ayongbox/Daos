package io.github.ayongbox.daos.web.auth;

import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.runtime.permission.PermissionRuleMatcher;
import io.github.ayongbox.daos.runtime.permission.PermissionRuleMatcher.SubjectMatch;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;

/**
 * 把 Baseon 的全局权限串（{@code "table:read"}）适配到 Daos 的应用级权限上下文。
 *
 * <p>权限语义仍由 Daos 引擎裁决：拒绝优先、默认拒绝。请求尚未解析出权限上下文时一律拒绝。</p>
 */
public final class DaosPermissionChecker implements PermissionChecker {

    private final Clock clock;

    public DaosPermissionChecker() {
        this(Clock.systemUTC());
    }

    public DaosPermissionChecker(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        PermissionContext context = currentContext();
        if (context == null) {
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

    private static PermissionContext currentContext() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        HttpServletRequest request = servlet.getRequest();
        return PermissionContextHolder.get(request);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
