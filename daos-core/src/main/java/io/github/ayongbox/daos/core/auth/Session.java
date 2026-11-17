package io.github.ayongbox.daos.core.auth;

import java.time.Instant;
import java.util.Objects;

/**
 * 登录会话。仓储只保存令牌哈希，明文令牌仅在登录响应中出现一次。
 */
public record Session(
        String sessionId,
        String tenantId,
        String subjectId,
        String username,
        String memberId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt,
        String clientIp,
        String userAgent) {

    public Session {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(tokenHash, "tokenHash");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (tokenHash.isBlank()) {
            throw new IllegalArgumentException("会话令牌哈希不能为空");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("会话过期时间必须晚于签发时间");
        }
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
