package io.github.ayongbox.daos.core.spi;

import io.github.ayongbox.daos.core.metadata.app.SchemaApp;

import java.util.Optional;

/**
 * 应用草稿仓储。只负责存取草稿，不做校验、不做发布。
 *
 * <p>草稿可反复覆盖；发布链路的读取入口永远是 {@link SnapshotStore}，草稿不得被运行时直接消费。</p>
 */
public interface MetadataStore {

    Optional<SchemaApp> findDraft(String appId);

    void saveDraft(SchemaApp app);
}
