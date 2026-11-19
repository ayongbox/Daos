package io.github.ayongbox.daos.web.config;

import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.spi.AccountStore;
import io.github.ayongbox.daos.core.spi.AuditStore;
import io.github.ayongbox.daos.core.spi.DictionaryStore;
import io.github.ayongbox.daos.core.spi.FileStore;
import io.github.ayongbox.daos.core.spi.IdentityStore;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.MigrationStore;
import io.github.ayongbox.daos.core.spi.SessionStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcAccountStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcAuditStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcDictionaryStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcFileStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcIdentityStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcMetadataStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcMigrationStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcSessionStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcSnapshotStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcStoreSchema;
import io.github.ayongbox.daos.runtime.store.jdbc.JsonCodec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 持久化装配。所有 SPI 仓储统一落到 DataSource，启动时幂等建表。
 */
@Configuration
public class PersistenceConfiguration {

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JsonCodec jsonCodec() {
        return new JsonCodec();
    }

    /**
     * 非懒加载单例，保证上下文刷新阶段完成建表，早于引导账号与 HTTP 请求。
     */
    @Bean
    public JdbcStoreSchema jdbcStoreSchema(DataSource dataSource, SqlDialect sqlDialect) {
        return new JdbcStoreSchema(dataSource, sqlDialect.type());
    }

    @Bean
    public MetadataStore metadataStore(JdbcTemplate jdbcTemplate, JsonCodec jsonCodec) {
        return new JdbcMetadataStore(jdbcTemplate, jsonCodec);
    }

    @Bean
    public SnapshotStore snapshotStore(JdbcTemplate jdbcTemplate, JsonCodec jsonCodec) {
        return new JdbcSnapshotStore(jdbcTemplate, jsonCodec);
    }

    @Bean
    public MigrationStore migrationStore(JdbcTemplate jdbcTemplate, JsonCodec jsonCodec) {
        return new JdbcMigrationStore(jdbcTemplate, jsonCodec);
    }

    @Bean
    public IdentityStore identityStore(JdbcTemplate jdbcTemplate, JsonCodec jsonCodec) {
        return new JdbcIdentityStore(jdbcTemplate, jsonCodec);
    }

    @Bean
    public AccountStore accountStore(JdbcTemplate jdbcTemplate, JsonCodec jsonCodec) {
        return new JdbcAccountStore(jdbcTemplate, jsonCodec);
    }

    @Bean
    public DictionaryStore dictionaryStore(JdbcTemplate jdbcTemplate, JsonCodec jsonCodec) {
        return new JdbcDictionaryStore(jdbcTemplate, jsonCodec);
    }

    @Bean
    public SessionStore sessionStore(JdbcTemplate jdbcTemplate, JsonCodec jsonCodec) {
        return new JdbcSessionStore(jdbcTemplate, jsonCodec);
    }

    @Bean
    public AuditStore auditStore(JdbcTemplate jdbcTemplate, JsonCodec jsonCodec) {
        return new JdbcAuditStore(jdbcTemplate, jsonCodec);
    }

    @Bean
    public FileStore fileStore(JdbcTemplate jdbcTemplate, JsonCodec jsonCodec) {
        return new JdbcFileStore(jdbcTemplate, jsonCodec);
    }
}
