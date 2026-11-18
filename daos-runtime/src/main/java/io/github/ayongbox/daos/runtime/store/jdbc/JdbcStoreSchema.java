package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.pipeline.Dialect;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * 仓储表结构初始化。启动时幂等执行，不依赖外部迁移工具。
 *
 * <p>大文本列按方言选择：H2 用 CLOB，PostgreSQL 用 TEXT，MySQL 用 LONGTEXT。</p>
 */
public final class JdbcStoreSchema {

    public JdbcStoreSchema(DataSource dataSource, Dialect dialect) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String ddl : ddl(dialect)) {
            jdbc.execute(ddl);
        }
    }

    private static List<String> ddl(Dialect dialect) {
        String largeText = switch (dialect) {
            case H2 -> "CLOB";
            case MYSQL -> "LONGTEXT";
            case POSTGRESQL -> "TEXT";
        };
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS daos_app (
                  app_id VARCHAR(64) NOT NULL,
                  code VARCHAR(64) NOT NULL,
                  deleted BOOLEAN NOT NULL,
                  updated_at VARCHAR(40) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (app_id)
                )
                """.formatted(largeText),
                """
                CREATE TABLE IF NOT EXISTS daos_snapshot (
                  snapshot_id VARCHAR(64) NOT NULL,
                  app_id VARCHAR(64) NOT NULL,
                  release_version BIGINT NOT NULL,
                  created_at VARCHAR(40) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (snapshot_id),
                  CONSTRAINT uq_daos_snapshot_version UNIQUE (app_id, release_version)
                )
                """.formatted(largeText),
                "CREATE INDEX IF NOT EXISTS idx_daos_snapshot_app ON daos_snapshot (app_id)",
                """
                CREATE TABLE IF NOT EXISTS daos_active_snapshot (
                  app_id VARCHAR(64) NOT NULL,
                  snapshot_id VARCHAR(64) NOT NULL,
                  updated_at VARCHAR(40) NOT NULL,
                  PRIMARY KEY (app_id)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS daos_migration (
                  migration_id VARCHAR(64) NOT NULL,
                  app_id VARCHAR(64) NOT NULL,
                  created_at VARCHAR(40) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (migration_id)
                )
                """.formatted(largeText),
                "CREATE INDEX IF NOT EXISTS idx_daos_migration_app ON daos_migration (app_id)",
                """
                CREATE TABLE IF NOT EXISTS daos_department (
                  tenant_id VARCHAR(64) NOT NULL,
                  department_id VARCHAR(64) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (tenant_id, department_id)
                )
                """.formatted(largeText),
                """
                CREATE TABLE IF NOT EXISTS daos_member (
                  tenant_id VARCHAR(64) NOT NULL,
                  member_id VARCHAR(64) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (tenant_id, member_id)
                )
                """.formatted(largeText),
                """
                CREATE TABLE IF NOT EXISTS daos_role (
                  tenant_id VARCHAR(64) NOT NULL,
                  role_id VARCHAR(64) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (tenant_id, role_id)
                )
                """.formatted(largeText),
                """
                CREATE TABLE IF NOT EXISTS daos_role_binding (
                  tenant_id VARCHAR(64) NOT NULL,
                  binding_id VARCHAR(64) NOT NULL,
                  member_id VARCHAR(64) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (tenant_id, binding_id)
                )
                """.formatted(largeText),
                """
                CREATE INDEX IF NOT EXISTS idx_daos_role_binding_member
                  ON daos_role_binding (tenant_id, member_id)
                """,
                """
                CREATE TABLE IF NOT EXISTS daos_account (
                  tenant_id VARCHAR(64) NOT NULL,
                  account_id VARCHAR(64) NOT NULL,
                  username VARCHAR(64) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (tenant_id, account_id),
                  CONSTRAINT uq_daos_account_username UNIQUE (tenant_id, username)
                )
                """.formatted(largeText),
                """
                CREATE TABLE IF NOT EXISTS daos_session (
                  session_id VARCHAR(64) NOT NULL,
                  token_hash VARCHAR(128) NOT NULL,
                  expires_at VARCHAR(40) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (session_id),
                  CONSTRAINT uq_daos_session_token UNIQUE (token_hash)
                )
                """.formatted(largeText),
                """
                CREATE TABLE IF NOT EXISTS daos_audit (
                  event_id VARCHAR(64) NOT NULL,
                  tenant_id VARCHAR(64) NOT NULL,
                  occurred_at VARCHAR(40) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (event_id)
                )
                """.formatted(largeText),
                """
                CREATE INDEX IF NOT EXISTS idx_daos_audit_tenant_time
                  ON daos_audit (tenant_id, occurred_at)
                """,
                """
                CREATE TABLE IF NOT EXISTS daos_file (
                  file_id VARCHAR(64) NOT NULL,
                  tenant_id VARCHAR(64) NOT NULL,
                  app_id VARCHAR(64) NOT NULL,
                  payload %s NOT NULL,
                  PRIMARY KEY (file_id)
                )
                """.formatted(largeText));
    }
}
