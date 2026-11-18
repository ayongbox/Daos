package io.github.ayongbox.daos.web.config;

import io.github.ayongbox.daos.core.pipeline.ddl.MigrationCompiler;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.MigrationStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.engine.SchemaEvolutionService;
import io.github.ayongbox.daos.runtime.engine.ddl.DefaultMigrationCompiler;
import io.github.ayongbox.daos.runtime.engine.dialect.H2Dialect;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.exec.DataWriteService;
import io.github.ayongbox.daos.runtime.form.FormRenderService;
import io.github.ayongbox.daos.runtime.menu.MenuService;
import io.github.ayongbox.daos.runtime.view.ViewService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 数据访问装配。连接池由 Spring Boot 自动配置提供；方言按数据源类型选择，
 * H2 仅用于本地开发与测试，生产仍使用 PostgreSQL。
 */
@Configuration
public class DataConfiguration {

    @Bean
    public SqlDialect sqlDialect(@Value("${spring.datasource.url:}") String jdbcUrl) {
        return jdbcUrl.startsWith("jdbc:h2:")
                ? new H2Dialect()
                : new PostgresDialect();
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

    @Bean
    public PublishService publishService(MetadataStore metadataStore, SnapshotStore snapshotStore) {
        return new PublishService(metadataStore, snapshotStore);
    }

    @Bean
    public FormRenderService formRenderService(SnapshotStore snapshotStore) {
        return new FormRenderService(snapshotStore);
    }

    @Bean
    public ViewService viewService(SnapshotStore snapshotStore, DataQueryService dataQueryService) {
        return new ViewService(snapshotStore, dataQueryService);
    }

    @Bean
    public MenuService menuService(SnapshotStore snapshotStore) {
        return new MenuService(snapshotStore);
    }

    @Bean
    public MigrationCompiler migrationCompiler() {
        return new DefaultMigrationCompiler();
    }

    @Bean
    public SchemaEvolutionService schemaEvolutionService(
            MetadataStore metadataStore,
            SnapshotStore snapshotStore,
            MigrationStore migrationStore,
            MigrationCompiler migrationCompiler,
            DataAccessFacade dataAccessFacade,
            SqlDialect sqlDialect) {
        return new SchemaEvolutionService(
                metadataStore, snapshotStore, migrationStore, migrationCompiler,
                dataAccessFacade, sqlDialect);
    }
}
