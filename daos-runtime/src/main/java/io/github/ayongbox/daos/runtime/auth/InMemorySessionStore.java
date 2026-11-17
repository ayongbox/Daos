package io.github.ayongbox.daos.runtime.auth;

import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.spi.SessionStore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话仓储，按令牌哈希查找。生产环境替换为持久化实现。
 */
public final class InMemorySessionStore implements SessionStore {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(Session session) {
        Objects.requireNonNull(session, "session");
        sessions.put(session.sessionId(), session);
    }

    @Override
    public Optional<Session> findByTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash");
        return sessions.values().stream()
                .filter(session -> session.tokenHash().equals(tokenHash))
                .findFirst();
    }

    @Override
    public void delete(String sessionId) {
        sessions.remove(sessionId);
    }
}
