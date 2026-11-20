package io.github.ayongbox.daos.core.spi;

import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * 发布快照仓储。快照只追加，不覆盖；当前生效版本由独立指针维护。
 *
 * <p>同一应用不允许出现重复的 {@code releaseVersion}，同一 {@code snapshotId} 不允许重复写入。</p>
 */
public interface SnapshotStore {

    void append(SchemaSnapshot snapshot);

    Optional<SchemaSnapshot> findById(String snapshotId);

    Optional<SchemaSnapshot> findActive(String appId);

    /**
     * 应用内全部发布快照，按 {@code releaseVersion} 倒序（最新在前）；无快照时返回空列表。
     */
    List<SchemaSnapshot> listByApp(String appId);

    /**
     * 应用内最新发布版本，无任何快照时返回 0。
     */
    long latestReleaseVersion(String appId);

    /**
     * 切换当前生效指针。快照必须已存在且属于同一应用。
     */
    void activate(String appId, String snapshotId);
}
