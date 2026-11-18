package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.core.spi.AccountStore;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 账号仓储的数据库实现。用户名在租户内唯一，由唯一约束兜底。
 */
public final class JdbcAccountStore implements AccountStore {

    private final JdbcTemplate jdbc;
    private final JsonCodec codec;

    public JdbcAccountStore(JdbcTemplate jdbc, JsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public Optional<Account> findById(String tenantId, String accountId) {
        return payloads("SELECT payload FROM daos_account WHERE tenant_id = ? AND account_id = ?",
                tenantId, accountId)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, Account.class));
    }

    @Override
    public Optional<Account> findByUsername(String tenantId, String username) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(username, "username");
        return payloads("SELECT payload FROM daos_account WHERE tenant_id = ? AND username = ?",
                tenantId, username)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, Account.class));
    }

    @Override
    public void save(Account account) {
        Objects.requireNonNull(account, "account");
        boolean duplicated = findByUsername(account.tenantId(), account.username())
                .filter(existing -> !existing.id().equals(account.id()))
                .isPresent();
        if (duplicated) {
            throw new IllegalStateException("用户名已存在: " + account.username());
        }

        String payload = codec.write(account);
        int updated = jdbc.update(
                "UPDATE daos_account SET username = ?, payload = ?"
                        + " WHERE tenant_id = ? AND account_id = ?",
                account.username(), payload, account.tenantId(), account.id());
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO daos_account (tenant_id, account_id, username, payload)"
                                + " VALUES (?, ?, ?, ?)",
                        account.tenantId(), account.id(), account.username(), payload);
            } catch (DuplicateKeyException e) {
                throw new IllegalStateException("用户名已存在: " + account.username(), e);
            }
        }
    }

    private List<String> payloads(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }
}
