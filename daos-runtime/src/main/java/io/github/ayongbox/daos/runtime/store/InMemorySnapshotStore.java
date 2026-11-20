package io.github.ayongbox.daos.runtime.store;

import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 发布快照仓储的内存实现。
 *
 * <p>写入即冻结：同一 {@code snapshotId} 或同一应用内重复的 {@code releaseVersion} 一律拒绝，
 * 不存在覆盖语义。</p>
 */
public final class InMemorySnapshotStore implements SnapshotStore {

    private final Map<String, SchemaSnapshot> byId = new HashMap<>();
    private final Map<String, List<SchemaSnapshot>> byApp = new HashMap<>();
    private final Map<String, String> activeByApp = new HashMap<>();

    @Override
    public synchronized void append(SchemaSnapshot snapshot) {
        if (byId.containsKey(snapshot.snapshotId())) {
            throw new IllegalStateException("快照已存在: " + snapshot.snapshotId());
        }

        List<SchemaSnapshot> snapshots = byApp.computeIfAbsent(snapshot.appId(), key -> new ArrayList<>());
        boolean duplicatedVersion = snapshots.stream()
                .anyMatch(existing -> existing.releaseVersion() == snapshot.releaseVersion());
        if (duplicatedVersion) {
            throw new IllegalStateException(
                    "应用 " + snapshot.appCode() + " 已存在发布版本 " + snapshot.releaseVersion());
        }

        byId.put(snapshot.snapshotId(), snapshot);
        snapshots.add(snapshot);
    }

    @Override
    public synchronized Optional<SchemaSnapshot> findById(String snapshotId) {
        return Optional.ofNullable(byId.get(snapshotId));
    }

    @Override
    public synchronized Optional<SchemaSnapshot> findActive(String appId) {
        String snapshotId = activeByApp.get(appId);
        return snapshotId == null ? Optional.empty() : findById(snapshotId);
    }

    @Override
    public synchronized List<SchemaSnapshot> listByApp(String appId) {
        return byApp.getOrDefault(appId, List.of()).stream()
                .sorted(Comparator.comparingLong(SchemaSnapshot::releaseVersion).reversed())
                .toList();
    }

    @Override
    public synchronized long latestReleaseVersion(String appId) {
        return byApp.getOrDefault(appId, List.of()).stream()
                .mapToLong(SchemaSnapshot::releaseVersion)
                .max()
                .orElse(0L);
    }

    @Override
    public synchronized void activate(String appId, String snapshotId) {
        SchemaSnapshot snapshot = byId.get(snapshotId);
        if (snapshot == null) {
            throw new IllegalStateException("快照不存在: " + snapshotId);
        }
        if (!snapshot.appId().equals(appId)) {
            throw new IllegalStateException(
                    "快照 " + snapshotId + " 属于应用 " + snapshot.appId() + "，不能激活到应用 " + appId);
        }
        activeByApp.put(appId, snapshotId);
    }
}
