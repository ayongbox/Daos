package io.github.ayongbox.daos.runtime.store;

import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.spi.MetadataStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 草稿仓储的内存实现。用于单机运行与测试，生产环境由持久化实现替换。
 */
public final class InMemoryMetadataStore implements MetadataStore {

    private final Map<String, SchemaApp> drafts = new HashMap<>();

    @Override
    public synchronized Optional<SchemaApp> findDraft(String appId) {
        return Optional.ofNullable(drafts.get(appId));
    }

    @Override
    public synchronized void saveDraft(SchemaApp app) {
        drafts.put(app.id(), app);
    }
}
