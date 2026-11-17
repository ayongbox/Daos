package io.github.ayongbox.daos.runtime.audit;

import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.spi.AuditStore;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存审计仓储，用于测试与单机场景。生产实现必须异步批量落库，避免阻塞业务请求。
 */
public final class InMemoryAuditStore implements AuditStore {

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(AuditEvent event) {
        events.add(event);
    }

    @Override
    public List<AuditEvent> recent(String tenantId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return events.stream()
                .filter(event -> event.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(AuditEvent::occurredAt).reversed()
                        .thenComparing(AuditEvent::id, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }
}
