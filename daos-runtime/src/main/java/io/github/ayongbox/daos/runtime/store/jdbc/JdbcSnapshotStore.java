package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 发布快照仓储的数据库实现。快照只追加，生效指针单独存放在 daos_active_snapshot。
 */
public final class JdbcSnapshotStore implements SnapshotStore {

    private final JdbcTemplate jdbc;
    private final JsonCodec codec;

    public JdbcSnapshotStore(JdbcTemplate jdbc, JsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void append(SchemaSnapshot snapshot) {
        if (exists("SELECT COUNT(*) FROM daos_snapshot WHERE snapshot_id = ?", snapshot.snapshotId())) {
            throw new IllegalStateException("快照已存在: " + snapshot.snapshotId());
        }
        if (exists("SELECT COUNT(*) FROM daos_snapshot WHERE app_id = ? AND release_version = ?",
                snapshot.appId(), snapshot.releaseVersion())) {
            throw new IllegalStateException(
                    "应用 " + snapshot.appCode() + " 已存在发布版本 " + snapshot.releaseVersion());
        }
        try {
            jdbc.update(
                    "INSERT INTO daos_snapshot (snapshot_id, app_id, release_version, created_at, payload)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    snapshot.snapshotId(), snapshot.appId(), snapshot.releaseVersion(),
                    snapshot.createdAt() == null ? "" : snapshot.createdAt(), codec.write(snapshot));
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("快照已存在: " + snapshot.snapshotId(), e);
        }
    }

    @Override
    public Optional<SchemaSnapshot> findById(String snapshotId) {
        return payloads("SELECT payload FROM daos_snapshot WHERE snapshot_id = ?", snapshotId)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, SchemaSnapshot.class));
    }

    @Override
    public Optional<SchemaSnapshot> findActive(String appId) {
        return payloads(
                "SELECT s.payload FROM daos_snapshot s"
                        + " JOIN daos_active_snapshot a ON a.snapshot_id = s.snapshot_id"
                        + " WHERE a.app_id = ?", appId)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, SchemaSnapshot.class));
    }

    @Override
    public List<SchemaSnapshot> listByApp(String appId) {
        return payloads(
                "SELECT payload FROM daos_snapshot WHERE app_id = ? ORDER BY release_version DESC", appId)
                .stream()
                .map(payload -> codec.read(payload, SchemaSnapshot.class))
                .toList();
    }

    @Override
    public long latestReleaseVersion(String appId) {
        Long version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(release_version), 0) FROM daos_snapshot WHERE app_id = ?",
                Long.class, appId);
        return version == null ? 0L : version;
    }

    @Override
    public void activate(String appId, String snapshotId) {
        SchemaSnapshot snapshot = findById(snapshotId)
                .orElseThrow(() -> new IllegalStateException("快照不存在: " + snapshotId));
        if (!snapshot.appId().equals(appId)) {
            throw new IllegalStateException(
                    "快照 " + snapshotId + " 属于应用 " + snapshot.appId() + "，不能激活到应用 " + appId);
        }
        String now = Instant.now().toString();
        int updated = jdbc.update(
                "UPDATE daos_active_snapshot SET snapshot_id = ?, updated_at = ? WHERE app_id = ?",
                snapshotId, now, appId);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO daos_active_snapshot (app_id, snapshot_id, updated_at) VALUES (?, ?, ?)",
                    appId, snapshotId, now);
        }
    }

    private boolean exists(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count != null && count > 0;
    }

    private List<String> payloads(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }
}
