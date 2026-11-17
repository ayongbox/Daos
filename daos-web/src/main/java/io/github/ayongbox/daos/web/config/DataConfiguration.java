package io.github.ayongbox.daos.web.config;

import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.exec.DataWriteService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 数据访问装配。连接池由 Spring Boot 自动配置提供，方言默认 PostgreSQL。
 */
@Configuration
public class DataConfiguration {

    @Bean
    public SqlDialect sqlDialect() {
        return new PostgresDialect();
    }

    @Bean
    public DataAccessFacade dataAccessFacade(DataSource dataSource) {
        return new DataAccessFacade(dataSource);
    }

    @Bean
    public DataQueryService dataQueryService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccessFacade,
            SqlDialect sqlDialect,
            AuditLog auditLog) {
        return new DataQueryService(snapshotStore, dataAccessFacade, sqlDialect, auditLog);
    }

    @Bean
    public DataWriteService dataWriteService(
            SnapshotStore snapshotStore,
            DataAccessFacade dataAccessFacade,
            SqlDialect sqlDialect,
            AuditLog auditLog) {
        return new DataWriteService(snapshotStore, dataAccessFacade, sqlDialect, auditLog);
    }
}
