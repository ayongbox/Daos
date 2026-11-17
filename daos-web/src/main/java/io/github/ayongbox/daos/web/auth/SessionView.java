package io.github.ayongbox.daos.web.auth;

import io.github.ayongbox.daos.core.auth.Session;

import java.time.Instant;

/**
 * 当前会话视图，供前端刷新页面后恢复登录态。
 */
public record SessionView(
        String sessionId,
        String tenantId,
        String subjectId,
        Instant issuedAt,
        Instant expiresAt) {

    public static SessionView of(Session session) {
        return new SessionView(
                session.sessionId(), session.tenantId(), session.subjectId(),
                session.issuedAt(), session.expiresAt());
    }
}
