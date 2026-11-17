package io.github.ayongbox.daos.core.audit;

import io.github.ayongbox.daos.core.metadata.common.ResourceType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 审计事件。登录失败等场景允许 {@code subjectId} 为空字符串，表示主体未知。
 *
 * <p>{@code detail} 只放轻量上下文（表编码、记录 id、行数等），不得写入业务字段原值。</p>
 */
public record AuditEvent(
        String id,
        String tenantId,
        String appId,
        String subjectId,
        AuditAction action,
        ResourceType resourceType,
        String resourceId,
        AuditOutcome outcome,
        String message,
        String clientIp,
        String userAgent,
        String traceId,
        Instant occurredAt,
        Map<String, Object> detail) {

    public AuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(occurredAt, "occurredAt");
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }
}
