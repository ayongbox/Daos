package io.github.ayongbox.daos.web.config;

import io.github.ayongbox.daos.core.auth.PasswordHasher;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.spi.AccountStore;
import io.github.ayongbox.daos.core.spi.AuditStore;
import io.github.ayongbox.daos.core.spi.DictionaryStore;
import io.github.ayongbox.daos.core.spi.IdentityStore;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SessionStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.app.AppService;
import io.github.ayongbox.daos.runtime.auth.AuthenticationService;
import io.github.ayongbox.daos.runtime.auth.Pbkdf2PasswordHasher;
import io.github.ayongbox.daos.runtime.dictionary.DictionaryAdminService;
import io.github.ayongbox.daos.runtime.identity.IdentityAdminQueryService;
import io.github.ayongbox.daos.runtime.identity.IdentityAdminService;
import io.github.ayongbox.daos.runtime.identity.PermissionContextFactory;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.baseon.starter.trace.TraceIdUtil;
import io.github.ayongbox.daos.web.auth.DaosPermissionChecker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * 认证相关装配。仓储由 {@link PersistenceConfiguration} 提供，本类只负责服务与权限链路。
 *
 * <p>通过 {@code daos.auth.bootstrap-*} 配置提供首个账号，未配置时不创建任何账号。</p>
 */
@Configuration
public class AuthConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthConfiguration.class);

    @Bean
    public PasswordHasher passwordHasher() {
        return new Pbkdf2PasswordHasher();
    }

    @Bean
    public AuditLog auditLog(AuditStore auditStore) {
        return new AuditLog(auditStore, Clock.systemUTC(), TraceIdUtil::get);
    }

    @Bean
    public PermissionContextFactory permissionContextFactory(IdentityStore identityStore) {
        return new PermissionContextFactory(identityStore);
    }

    @Bean
    public SessionPermissionService sessionPermissionService(
            SnapshotStore snapshotStore,
            MetadataStore metadataStore,
            PermissionContextFactory permissionContextFactory) {
        return new SessionPermissionService(snapshotStore, metadataStore, permissionContextFactory);
    }

    @Bean
    public AppService appService(
            MetadataStore metadataStore,
            IdentityStore identityStore,
            SnapshotStore snapshotStore) {
        return new AppService(metadataStore, identityStore, snapshotStore, Clock.systemUTC());
    }

    @Bean
    public PermissionChecker permissionChecker(SessionPermissionService sessionPermissionService) {
        return new DaosPermissionChecker(sessionPermissionService);
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
    public IdentityAdminService identityAdminService(
            IdentityStore identityStore,
            AccountStore accountStore,
            PasswordHasher passwordHasher,
            AuditLog auditLog) {
        return new IdentityAdminService(
                identityStore, accountStore, passwordHasher, auditLog, Clock.systemUTC());
    }

    @Bean
    public IdentityAdminQueryService identityAdminQueryService(IdentityStore identityStore) {
        return new IdentityAdminQueryService(identityStore);
    }

    @Bean
    public DictionaryAdminService dictionaryAdminService(DictionaryStore dictionaryStore) {
        return new DictionaryAdminService(dictionaryStore, Clock.systemUTC());
    }

    @Bean
    public ApplicationRunner bootstrapAccount(
            AccountStore accountStore,
            IdentityStore identityStore,
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
            String now = Instant.now().toString();
            OrgMember member = new OrgMember(
                    Ids.ulid(), tenantId, username, username, null, null, null,
                    MemberStatus.ACTIVE, Map.of(), "bootstrap", now, "bootstrap", now, false, 1);
            identityStore.saveMember(member);
            accountStore.save(new Account(
                    Ids.ulid(), tenantId, username, username,
                    passwordHasher.hash(password), member.id(), true));
            log.info("已创建引导账号 {}（租户 {}）", username, tenantId);
        };
    }
}
