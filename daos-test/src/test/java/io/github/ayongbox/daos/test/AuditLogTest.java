package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void recordsEventWithGeneratedIdAndFixedTime() {
        InMemoryAuditStore store = new InMemoryAuditStore();
        AuditLog log = new AuditLog(store, Clock.fixed(NOW, ZoneOffset.UTC));

        log.record("t1", "app1", "u1", AuditAction.CREATE, ResourceType.TABLE, "r1",
                AuditOutcome.SUCCESS, null, Map.of("tableId", "t1"));

        List<AuditEvent> events = store.recent("t1", 10);
        assertEquals(1, events.size());
        AuditEvent event = events.get(0);
        assertTrue(Ids.isUlid(event.id()));
        assertEquals(NOW, event.occurredAt());
        assertEquals("app1", event.appId());
        assertEquals("r1", event.resourceId());
        assertEquals("t1", event.detail().get("tableId"));
    }

    @Test
    void recentFiltersTenantAndLimits() {
        InMemoryAuditStore store = new InMemoryAuditStore();
        AuditLog log = new AuditLog(store, Clock.fixed(NOW, ZoneOffset.UTC));

        log.record("t1", "app1", "u1", AuditAction.READ, ResourceType.TABLE, "a", AuditOutcome.SUCCESS, null);
        log.record("t1", "app1", "u1", AuditAction.READ, ResourceType.TABLE, "b", AuditOutcome.SUCCESS, null);
        log.record("t2", "app1", "u2", AuditAction.READ, ResourceType.TABLE, "c", AuditOutcome.SUCCESS, null);

        List<AuditEvent> tenantOne = store.recent("t1", 10);
        assertEquals(2, tenantOne.size());
        assertEquals(1, store.recent("t1", 1).size());
        assertEquals(1, store.recent("t2", 10).size());
        assertTrue(store.recent("t1", 0).isEmpty());
    }

    @Test
    void noopLogDiscardsEvents() {
        AuditLog.noop().record("t1", "app1", "u1", AuditAction.LOGIN,
                null, null, AuditOutcome.SUCCESS, null);
    }
}
