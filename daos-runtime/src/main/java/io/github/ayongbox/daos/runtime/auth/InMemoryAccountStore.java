package io.github.ayongbox.daos.runtime.auth;

import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.core.spi.AccountStore;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存账号仓储，用于单机与测试。生产环境替换为持久化实现。
 */
public final class InMemoryAccountStore implements AccountStore {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findById(String tenantId, String accountId) {
        return Optional.ofNullable(accounts.get(key(tenantId, accountId)));
    }

    @Override
    public Optional<Account> findByUsername(String tenantId, String username) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(username, "username");
        return accounts.values().stream()
                .filter(account -> account.tenantId().equals(tenantId) && account.username().equals(username))
                .findFirst();
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
        accounts.put(key(account.tenantId(), account.id()), account);
    }

    private static String key(String tenantId, String accountId) {
        return tenantId + "\u0000" + accountId;
    }
}
