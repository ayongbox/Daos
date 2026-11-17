package io.github.ayongbox.daos.core.spi;

import io.github.ayongbox.daos.core.metadata.identity.Account;

import java.util.Optional;

/**
 * 本地账号仓储。账号按租户隔离，用户名在租户内唯一。
 */
public interface AccountStore {

    Optional<Account> findById(String tenantId, String accountId);

    Optional<Account> findByUsername(String tenantId, String username);

    void save(Account account);
}
