package io.github.ayongbox.daos.web.auth;

import java.time.Instant;

/**
 * 登录响应。令牌只在这里出现一次，客户端需自行持久化。
 */
public record LoginResponse(
        String token,
        String tenantId,
        String subjectId,
        Instant expiresAt) {
}
