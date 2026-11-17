package io.github.ayongbox.daos.core.migration;

import java.util.Objects;

/**
 * 迁移台账记录。一次成功的迁移指向一对快照，回滚以该记录为准。
 *
 * <p>{@code fromSnapshotId} 为 null 表示应用首次发布；{@code error} 只在 FAILED 时非空。</p>
 */
public record MigrationRecord(
        String id,
        String appId,
        String fromSnapshotId,
        long fromVersion,
        String toSnapshotId,
        long toVersion,
        MigrationStatus status,
        int stepCount,
        String operator,
        String createdAt,
        String updatedAt,
        String error) {

    public MigrationRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public MigrationRecord withStatus(MigrationStatus newStatus, String now, String message) {
        return new MigrationRecord(
                id, appId, fromSnapshotId, fromVersion, toSnapshotId, toVersion,
                newStatus, stepCount, operator, createdAt, now, message);
    }
}
