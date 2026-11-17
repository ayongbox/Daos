package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;
import io.github.ayongbox.daos.runtime.auth.AuthenticationException;
import io.github.ayongbox.daos.runtime.auth.AuthenticationService;
import io.github.ayongbox.daos.runtime.auth.InMemoryAccountStore;
import io.github.ayongbox.daos.runtime.auth.InMemorySessionStore;
import io.github.ayongbox.daos.runtime.auth.Pbkdf2PasswordHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceTest {

    private static final String TENANT = "t1";
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(100_000);
    private InMemoryAccountStore accountStore;
    private InMemoryAuditStore auditStore;
    private AuthenticationService service;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        accountStore = new InMemoryAccountStore();
        auditStore = new InMemoryAuditStore();
        clock = new MutableClock(START);
        accountStore.save(new Account(
                "acc-1", TENANT, "alice", "Alice", hasher.hash("secret-123"), true));
        accountStore.save(new Account(
                "acc-2", TENANT, "bob", "Bob", hasher.hash("secret-123"), false));

        service = new AuthenticationService(
                accountStore, new InMemorySessionStore(), hasher,
                new AuditLog(auditStore), clock, Duration.ofHours(1));
    }

    @Test
    void loginIssuesSessionAndRecordsAudit() {
        AuthenticationService.LoginResult result = service.login(
                TENANT, "alice", "secret-123", "10.0.0.1", "JUnit");

        assertEquals("acc-1", result.session().subjectId());
        assertTrue(result.token().length() >= 40);
        assertNotEquals(result.token(), result.session().tokenHash());
        assertEquals(Optional.empty(), service.authenticate("wrong-token"));
        assertTrue(service.authenticate(result.token()).isPresent());

        AuditEvent event = singleEvent();
        assertEquals(AuditAction.LOGIN, event.action());
        assertEquals(AuditOutcome.SUCCESS, event.outcome());
        assertEquals("acc-1", event.subjectId());
        assertEquals("10.0.0.1", event.clientIp());
        assertEquals("JUnit", event.userAgent());
        assertEquals("alice", event.detail().get("username"));
    }

    @Test
    void wrongPasswordIsRejectedAndAudited() {
        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> service.login(TENANT, "alice", "bad-password", "10.0.0.1", "JUnit"));

        assertEquals("账号或密码错误", error.getMessage());
        AuditEvent event = singleEvent();
        assertEquals(AuditOutcome.FAILURE, event.outcome());
        assertEquals(AuditAction.LOGIN, event.action());
    }

    @Test
    void unknownAccountGetsSameMessage() {
        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> service.login(TENANT, "nobody", "secret-123", null, null));

        assertEquals("账号或密码错误", error.getMessage());
        assertEquals("", singleEvent().subjectId());
    }

    @Test
    void disabledAccountIsRejected() {
        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> service.login(TENANT, "bob", "secret-123", null, null));

        assertEquals("账号已停用", error.getMessage());
        assertEquals(AuditOutcome.FAILURE, singleEvent().outcome());
    }

    @Test
    void expiredSessionIsInvalidated() {
        AuthenticationService.LoginResult result = service.login(
                TENANT, "alice", "secret-123", null, null);
        clock.advance(Duration.ofHours(1));

        assertTrue(service.authenticate(result.token()).isEmpty());
        assertTrue(service.authenticate(result.token()).isEmpty());
    }

    @Test
    void logoutRevokesSessionAndRecordsAudit() {
        AuthenticationService.LoginResult result = service.login(
                TENANT, "alice", "secret-123", "10.0.0.1", "JUnit");

        service.logout(result.token());

        assertTrue(service.authenticate(result.token()).isEmpty());
        List<AuditEvent> events = auditStore.recent(TENANT, 10);
        assertEquals(2, events.size());
        AuditEvent logout = events.get(0);
        assertEquals(AuditAction.LOGOUT, logout.action());
        assertEquals(AuditOutcome.SUCCESS, logout.outcome());
        assertEquals("acc-1", logout.subjectId());
        assertEquals(result.session().sessionId(), logout.resourceId());
    }

    @Test
    void logoutUnknownTokenFails() {
        assertThrows(AuthenticationException.class, () -> service.logout("unknown-token"));
    }

    private AuditEvent singleEvent() {
        List<AuditEvent> events = auditStore.recent(TENANT, 10);
        assertEquals(1, events.size());
        return events.get(0);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
