package io.github.ayongbox.daos.core.spi;

import io.github.ayongbox.daos.core.audit.AuditEvent;

import java.util.List;

/**
 * 审计仓储。生产实现应异步落库并按租户、时间建立索引。
 */
public interface AuditStore {

    void append(AuditEvent event);

    /**
     * 最近事件，按发生时间倒序，最多返回 {@code limit} 条。
     */
    List<AuditEvent> recent(String tenantId, int limit);
}
