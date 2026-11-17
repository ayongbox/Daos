package io.github.ayongbox.daos.core.snapshot;

import io.github.ayongbox.daos.core.metadata.app.SchemaApp;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 发布快照。一经生成即不可变，运行时只读快照，草稿永不进入运行链路。
 *
 * <p>{@code releaseVersion} 在应用内单调递增；当前生效版本由仓储中的指针维护，
 * 回滚只切换指针，不改写任何快照。</p>
 */
public record SchemaSnapshot(
        String snapshotId,
        String appId,
        String appCode,
        long releaseVersion,
        int schemaVersion,
        String contentHash,
        String createdBy,
        String createdAt,
        SchemaApp app) {

    private static final Pattern CONTENT_HASH = Pattern.compile("[0-9a-f]{64}");

    public SchemaSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(appCode, "appCode");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(app, "app");

        if (snapshotId.isBlank()) {
            throw new IllegalArgumentException("快照标识不能为空");
        }
        if (releaseVersion < 1) {
            throw new IllegalArgumentException("发布版本必须为正数，当前 " + releaseVersion);
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("契约版本必须为正数，当前 " + schemaVersion);
        }
        if (!CONTENT_HASH.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("内容哈希必须是 64 位小写十六进制: " + contentHash);
        }
        if (!app.id().equals(appId) || !app.code().equals(appCode)) {
            throw new IllegalArgumentException("快照的应用标识与实际应用元数据不一致");
        }
        if (app.schemaVersion() != schemaVersion) {
            throw new IllegalArgumentException(
                    "快照契约版本 " + schemaVersion + " 与应用契约版本 " + app.schemaVersion() + " 不一致");
        }
    }
}
