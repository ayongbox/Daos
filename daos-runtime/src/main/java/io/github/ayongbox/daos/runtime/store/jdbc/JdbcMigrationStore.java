package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.migration.MigrationRecord;
import io.github.ayongbox.daos.core.spi.MigrationStore;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 迁移台账的数据库实现。记录只追加与更新状态，不删除历史。
 */
public final class JdbcMigrationStore implements MigrationStore {

    private final JdbcTemplate jdbc;
    private final JsonCodec codec;

    public JdbcMigrationStore(JdbcTemplate jdbc, JsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void append(MigrationRecord record) {
        if (findById(record.id()).isPresent()) {
            throw new IllegalStateException("迁移记录已存在: " + record.id());
        }
        try {
            jdbc.update(
                    "INSERT INTO daos_migration (migration_id, app_id, created_at, payload)"
                            + " VALUES (?, ?, ?, ?)",
                    record.id(), record.appId(), record.createdAt(), codec.write(record));
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("迁移记录已存在: " + record.id(), e);
        }
    }

    @Override
    public void update(MigrationRecord record) {
        int updated = jdbc.update(
                "UPDATE daos_migration SET app_id = ?, created_at = ?, payload = ? WHERE migration_id = ?",
                record.appId(), record.createdAt(), codec.write(record), record.id());
        if (updated == 0) {
            throw new IllegalStateException("迁移记录不存在: " + record.id());
        }
    }

    @Override
    public Optional<MigrationRecord> findById(String migrationId) {
        return payloads("SELECT payload FROM daos_migration WHERE migration_id = ?", migrationId)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, MigrationRecord.class));
    }

    @Override
    public List<MigrationRecord> listByApp(String appId) {
        return payloads("SELECT payload FROM daos_migration WHERE app_id = ? ORDER BY created_at DESC", appId)
                .stream()
                .map(payload -> codec.read(payload, MigrationRecord.class))
                .toList();
    }

    private List<String> payloads(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }
}
