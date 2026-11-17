package io.github.ayongbox.daos.web.config;

import io.github.ayongbox.daos.core.auth.PasswordHasher;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.core.spi.AccountStore;
import io.github.ayongbox.daos.core.spi.AuditStore;
import io.github.ayongbox.daos.core.spi.SessionStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;
import io.github.ayongbox.daos.runtime.auth.AuthenticationService;
import io.github.ayongbox.daos.runtime.auth.InMemoryAccountStore;
import io.github.ayongbox.daos.runtime.auth.InMemorySessionStore;
import io.github.ayongbox.daos.runtime.auth.Pbkdf2PasswordHasher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证相关装配。当前使用内存仓储，持久化实现接入后替换这里的 Bean 即可。
 *
 * <p>通过 {@code daos.auth.bootstrap-*} 配置提供首个账号，未配置时不创建任何账号。</p>
 */
@Configuration
public class AuthConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthConfiguration.class);

    @Bean
    public AccountStore accountStore() {
        return new InMemoryAccountStore();
    }

    @Bean
    public SessionStore sessionStore() {
        return new InMemorySessionStore();
    }

    @Bean
    public PasswordHasher passwordHasher() {
        return new Pbkdf2PasswordHasher();
    }

    @Bean
    public AuditStore auditStore() {
        return new InMemoryAuditStore();
    }

    @Bean
    public AuditLog auditLog(AuditStore auditStore) {
        return new AuditLog(auditStore);
    }

    @Bean
    public AuthenticationService authenticationService(
            AccountStore accountStore,
            SessionStore sessionStore,
            PasswordHasher passwordHasher,
            AuditLog auditLog) {
        return new AuthenticationService(accountStore, sessionStore, passwordHasher, auditLog);
    }

    @Bean
    public ApplicationRunner bootstrapAccount(
            AccountStore accountStore,
            PasswordHasher passwordHasher,
            @Value("${daos.auth.bootstrap-tenant:}") String tenantId,
            @Value("${daos.auth.bootstrap-username:}") String username,
            @Value("${daos.auth.bootstrap-password:}") String password) {
        return args -> {
            if (tenantId.isBlank() || username.isBlank() || password.isBlank()) {
                return;
            }
            if (accountStore.findByUsername(tenantId, username).isPresent()) {
                return;
            }
            accountStore.save(new Account(
                    Ids.ulid(), tenantId, username, username, passwordHasher.hash(password), true));
            log.info("已创建引导账号 {}（租户 {}）", username, tenantId);
        };
    }
}
