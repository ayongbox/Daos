package io.github.ayongbox.daos.runtime.identity;

import io.github.ayongbox.daos.core.auth.PermissionChecker;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.runtime.permission.PermissionRuleMatcher;
import io.github.ayongbox.daos.runtime.permission.PermissionRuleMatcher.SubjectMatch;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于已构建权限上下文的裁决器。拒绝优先：任意一条命中的 DENY 规则都会否决访问，
 * 没有任何命中的 ALLOW 规则时默认拒绝。
 *
 * <p>本类只做规则裁决，不解析组织数据；组织展开由 {@link PermissionContextFactory} 完成。</p>
 */
public final class ContextPermissionChecker implements PermissionChecker {

    private final PermissionContext context;
    private final Clock clock;

    public ContextPermissionChecker(PermissionContext context) {
        this(context, Clock.systemUTC());
    }

    public ContextPermissionChecker(PermissionContext context, Clock clock) {
        this.context = Objects.requireNonNull(context, "context");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean isAllowed(String subjectId, ResourceRef resource, ActionType action) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(action, "action");
        if (!context.subjectId().equals(subjectId)) {
            return false;
        }

        Instant now = clock.instant();
        boolean allowed = false;
        for (SchemaPermission rule : context.permissions()) {
            if (!candidate(rule, resource, action, now)) {
                continue;
            }

            SubjectMatch match = PermissionRuleMatcher.matchSubject(rule.subject(), context);
            if (match == SubjectMatch.NO_MATCH) {
                continue;
            }
            if (match == SubjectMatch.UNKNOWN) {
                // 组织级主体尚未展开，显式拒绝对当前主体按拒绝处理；允许规则忽略。
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

    @Override
    public FieldMask resolveFieldMask(String subjectId, ResourceRef resource) {
        Objects.requireNonNull(resource, "resource");
        if (!context.subjectId().equals(subjectId)) {
            return FieldMask.empty();
        }

        Instant now = clock.instant();
        List<SchemaPermission> allows = new ArrayList<>();
        for (SchemaPermission rule : context.permissions()) {
            if (!candidate(rule, resource, ActionType.READ, now)
                    || rule.effect() != EffectType.ALLOW) {
                continue;
            }
            if (PermissionRuleMatcher.matchSubject(rule.subject(), context) == SubjectMatch.MATCH) {
                allows.add(rule);
            }
        }
        return PermissionRuleMatcher.mergeFieldMask(allows);
    }

    private boolean candidate(
            SchemaPermission rule, ResourceRef resource, ActionType action, Instant now) {
        return rule.enabled()
                && !rule.deleted()
                && rule.actions().contains(action)
                && PermissionRuleMatcher.matchesResource(rule.resource(), resource)
                && PermissionRuleMatcher.active(rule, now);
    }
}
