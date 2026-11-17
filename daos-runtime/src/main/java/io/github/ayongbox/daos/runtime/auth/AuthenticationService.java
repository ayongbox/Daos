package io.github.ayongbox.daos.runtime.auth;

import io.github.ayongbox.daos.core.audit.AuditAction;
import io.github.ayongbox.daos.core.audit.AuditEvent;
import io.github.ayongbox.daos.core.audit.AuditOutcome;
import io.github.ayongbox.daos.core.auth.PasswordHasher;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.core.spi.AccountStore;
import io.github.ayongbox.daos.core.spi.SessionStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 登录与会话服务。负责本地账号校验、会话签发与登出，并写入登录/登出审计。
 *
 * <p>明文令牌只在登录响应中返回一次，仓储与审计中只出现令牌哈希。</p>
 */
public final class AuthenticationService {

    public static final Duration DEFAULT_TTL = Duration.ofHours(8);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final AccountStore accountStore;
    private final SessionStore sessionStore;
    private final PasswordHasher passwordHasher;
    private final AuditLog auditLog;
    private final Clock clock;
    private final Duration ttl;

    public AuthenticationService(
            AccountStore accountStore,
            SessionStore sessionStore,
            PasswordHasher passwordHasher) {
        this(accountStore, sessionStore, passwordHasher, AuditLog.noop(),
                Clock.systemUTC(), DEFAULT_TTL);
    }

    public AuthenticationService(
            AccountStore accountStore,
            SessionStore sessionStore,
            PasswordHasher passwordHasher,
            AuditLog auditLog) {
        this(accountStore, sessionStore, passwordHasher, auditLog, Clock.systemUTC(), DEFAULT_TTL);
    }

    public AuthenticationService(
            AccountStore accountStore,
            SessionStore sessionStore,
            PasswordHasher passwordHasher,
            AuditLog auditLog,
            Clock clock,
            Duration ttl) {
        this.accountStore = Objects.requireNonNull(accountStore, "accountStore");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("会话有效期必须为正");
        }
    }

    public LoginResult login(
            String tenantId,
            String username,
            String rawPassword,
            String clientIp,
            String userAgent) {
        requireText(tenantId, "tenantId");
        requireText(username, "username");
        requireText(rawPassword, "rawPassword");

        Optional<Account> found = accountStore.findByUsername(tenantId, username);
        if (found.isEmpty()) {
            recordLogin(tenantId, "", AuditOutcome.FAILURE, "账号或密码错误", username, clientIp, userAgent);
            throw new AuthenticationException("账号或密码错误");
        }

        Account account = found.get();
        if (!account.enabled()) {
            recordLogin(tenantId, account.id(), AuditOutcome.FAILURE, "账号已停用", username, clientIp, userAgent);
            throw new AuthenticationException("账号已停用");
        }
        if (!passwordHasher.matches(rawPassword, account.passwordHash())) {
            recordLogin(tenantId, account.id(), AuditOutcome.FAILURE, "账号或密码错误", username, clientIp, userAgent);
            throw new AuthenticationException("账号或密码错误");
        }

        String token = newToken();
        Instant now = Instant.now(clock);
        Session session = new Session(
                Ids.ulid(), tenantId, account.id(), account.username(), account.memberId(), sha256(token),
                now, now.plus(ttl), clientIp, userAgent);
        sessionStore.save(session);
        recordLogin(tenantId, account.id(), AuditOutcome.SUCCESS, null, username, clientIp, userAgent);
        return new LoginResult(token, session);
    }

    /**
     * 校验令牌。过期会话会被删除并返回空，不抛异常，便于拦截器统一处理。
     */
    public Optional<Session> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<Session> found = sessionStore.findByTokenHash(sha256(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Session session = found.get();
        if (session.isExpired(Instant.now(clock))) {
            sessionStore.delete(session.sessionId());
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void logout(String rawToken) {
        requireText(rawToken, "rawToken");
        Optional<Session> found = sessionStore.findByTokenHash(sha256(rawToken));
        if (found.isEmpty()) {
            throw new AuthenticationException("会话不存在或已过期");
        }
        Session session = found.get();
        sessionStore.delete(session.sessionId());
        auditLog.record(new AuditEvent(
                Ids.ulid(), session.tenantId(), null, session.subjectId(),
                AuditAction.LOGOUT, null, session.sessionId(), AuditOutcome.SUCCESS, null,
                session.clientIp(), session.userAgent(), null, Instant.now(clock),
                Map.of("sessionId", session.sessionId())));
    }

    private void recordLogin(
            String tenantId,
            String subjectId,
            AuditOutcome outcome,
            String message,
            String username,
            String clientIp,
            String userAgent) {
        auditLog.record(new AuditEvent(
                Ids.ulid(), tenantId, null, subjectId, AuditAction.LOGIN, null, null,
                outcome, message, clientIp, userAgent, null, Instant.now(clock),
                Map.of("username", username)));
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    public record LoginResult(String token, Session session) {
    }
}
