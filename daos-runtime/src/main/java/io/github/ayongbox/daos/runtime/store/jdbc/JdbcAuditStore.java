package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.spi.AuditStore;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 审计仓储的数据库实现。按租户与发生时间建立索引，查询走索引倒序。
 */
public final class JdbcAuditStore implements AuditStore {

    private final JdbcTemplate jdbc;
    private final JsonCodec codec;

    public JdbcAuditStore(JdbcTemplate jdbc, JsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void append(AuditEvent event) {
        try {
            jdbc.update(
                    "INSERT INTO daos_audit (event_id, tenant_id, occurred_at, payload)"
                            + " VALUES (?, ?, ?, ?)",
                    event.id(), event.tenantId(), event.occurredAt().toString(), codec.write(event));
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("审计事件已存在: " + event.id(), e);
        }
    }

    @Override
    public List<AuditEvent> recent(String tenantId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbc.queryForList(
                        "SELECT payload FROM daos_audit WHERE tenant_id = ?"
                                + " ORDER BY occurred_at DESC, event_id DESC LIMIT ?",
                        String.class, tenantId, limit)
                .stream()
                .map(payload -> codec.read(payload, AuditEvent.class))
                .toList();
    }
}
