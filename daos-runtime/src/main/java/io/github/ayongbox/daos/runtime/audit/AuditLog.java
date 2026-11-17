package io.github.ayongbox.daos.runtime.audit;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.spi.AuditStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 审计写入入口。业务模块只通过本类记录事件，不直接依赖仓储实现。
 *
 * <p>默认构造使用空实现，便于单机或测试场景关闭审计；生产环境应注入持久化仓储。</p>
 */
public final class AuditLog {

    private static final AuditStore DISCARD = new AuditStore() {
        @Override
        public void append(AuditEvent event) {
        }

        @Override
        public List<AuditEvent> recent(String tenantId, int limit) {
            return List.of();
        }
    };

    private final AuditStore store;
    private final Clock clock;

    public AuditLog(AuditStore store) {
        this(store, Clock.systemUTC());
    }

    public AuditLog(AuditStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static AuditLog noop() {
        return new AuditLog(DISCARD);
    }

    public void record(
            String tenantId,
            String appId,
            String subjectId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            AuditOutcome outcome,
            String message) {
        record(tenantId, appId, subjectId, action, resourceType, resourceId, outcome, message, Map.of());
    }

    public void record(
            String tenantId,
            String appId,
            String subjectId,
            AuditAction action,
            ResourceType resourceType,
            String resourceId,
            AuditOutcome outcome,
            String message,
            Map<String, Object> detail) {
        store.append(new AuditEvent(
                Ids.ulid(), tenantId, appId, subjectId, action, resourceType, resourceId,
                outcome, message, null, null, null, Instant.now(clock), detail));
    }

    /**
     * 直接写入已带请求上下文的事件，供 Web 层补充 IP、User-Agent 与链路 id。
     */
    public void record(AuditEvent event) {
        store.append(Objects.requireNonNull(event, "event"));
    }
}
