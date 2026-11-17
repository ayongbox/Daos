package io.github.ayongbox.daos.core.spi;

import io.github.ayongbox.daos.core.auth.Session;

import java.util.Optional;

/**
 * 会话仓储。按令牌哈希查找，不保存明文令牌。
 */
public interface SessionStore {

    void save(Session session);

    Optional<Session> findByTokenHash(String tokenHash);

    void delete(String sessionId);
}
