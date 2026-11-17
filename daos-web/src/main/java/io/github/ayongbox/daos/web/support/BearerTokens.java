package io.github.ayongbox.daos.web.support;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * 从请求头解析 Bearer 令牌。
 */
public final class BearerTokens {

    private static final String PREFIX = "Bearer ";

    private BearerTokens() {
    }

    public static Optional<String> resolve(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
