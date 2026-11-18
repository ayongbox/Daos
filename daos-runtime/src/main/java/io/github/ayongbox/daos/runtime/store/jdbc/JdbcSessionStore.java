package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.spi.SessionStore;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 会话仓储的数据库实现。只保存令牌哈希，按哈希建唯一索引。
 */
public final class JdbcSessionStore implements SessionStore {

    private final JdbcTemplate jdbc;
    private final JsonCodec codec;

    public JdbcSessionStore(JdbcTemplate jdbc, JsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void save(Session session) {
        Objects.requireNonNull(session, "session");
        String payload = codec.write(session);
        int updated = jdbc.update(
                "UPDATE daos_session SET token_hash = ?, expires_at = ?, payload = ? WHERE session_id = ?",
                session.tokenHash(), session.expiresAt().toString(), payload, session.sessionId());
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO daos_session (session_id, token_hash, expires_at, payload)"
                                + " VALUES (?, ?, ?, ?)",
                        session.sessionId(), session.tokenHash(),
                        session.expiresAt().toString(), payload);
            } catch (DuplicateKeyException e) {
                throw new IllegalStateException("会话令牌冲突", e);
            }
        }
    }

    @Override
    public Optional<Session> findByTokenHash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash");
        return payloads("SELECT payload FROM daos_session WHERE token_hash = ?", tokenHash)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, Session.class));
    }

    @Override
    public void delete(String sessionId) {
        jdbc.update("DELETE FROM daos_session WHERE session_id = ?", sessionId);
    }

    private List<String> payloads(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }
}
