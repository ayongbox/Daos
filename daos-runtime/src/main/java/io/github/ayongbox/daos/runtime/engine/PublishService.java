package io.github.ayongbox.daos.runtime.engine;

import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 发布服务。把草稿编译为不可变快照并切换生效指针。
 *
 * <p>固定顺序：读取草稿 → 归一化校验 → 计算内容哈希 → 写入快照 → 切换 currentRelease。
 * 校验失败时不写入任何快照，当前生效版本保持不变。</p>
 *
 * <p>内容哈希相同的重复发布直接返回当前快照，不产生空版本。</p>
 */
public final class PublishService {

    private final MetadataStore metadataStore;
    private final SnapshotStore snapshotStore;
    private final SchemaNormalizer normalizer;
    private final SchemaContentHasher contentHasher;

    public PublishService(MetadataStore metadataStore, SnapshotStore snapshotStore) {
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.normalizer = new SchemaNormalizer();
        this.contentHasher = new SchemaContentHasher();
    }

    public SchemaSnapshot publish(String appId, String operator) {
        Objects.requireNonNull(appId, "appId");
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("发布人不能为空");
        }

        SchemaApp draft = metadataStore.findDraft(appId)
                .orElseThrow(() -> new IllegalStateException("应用草稿不存在: " + appId));

        long releaseVersion = snapshotStore.latestReleaseVersion(appId) + 1;
        String snapshotId = Ids.ulid();

        // 发布前必须通过归一化，非法结构在这里阻断，不会留下半成品快照。
        normalizer.normalize(draft.code(), snapshotId, releaseVersion, draft.tables(), draft.forms());

        String contentHash = contentHasher.hash(draft);
        Optional<SchemaSnapshot> active = snapshotStore.findActive(appId);
        if (active.isPresent() && active.get().contentHash().equals(contentHash)) {
            return active.get();
        }

        SchemaSnapshot snapshot = new SchemaSnapshot(
                snapshotId,
                appId,
                draft.code(),
                releaseVersion,
                draft.schemaVersion(),
                contentHash,
                operator,
                Instant.now().toString(),
                draft);

        snapshotStore.append(snapshot);
        snapshotStore.activate(appId, snapshot.snapshotId());
        return snapshot;
    }
}
