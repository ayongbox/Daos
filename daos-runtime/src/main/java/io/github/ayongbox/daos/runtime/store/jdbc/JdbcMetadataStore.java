package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.spi.MetadataStore;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 草稿仓储的数据库实现。payload 保存完整聚合，code/deleted 抽列用于列表与过滤。
 */
public final class JdbcMetadataStore implements MetadataStore {

    private static final String COLUMNS = "app_id, code, deleted, updated_at, payload";

    private final JdbcTemplate jdbc;
    private final JsonCodec codec;

    public JdbcMetadataStore(JdbcTemplate jdbc, JsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public Optional<SchemaApp> findDraft(String appId) {
        return payloads("SELECT payload FROM daos_app WHERE app_id = ? AND deleted = FALSE", appId)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, SchemaApp.class));
    }

    @Override
    public List<SchemaApp> findAllDrafts() {
        return payloads("SELECT payload FROM daos_app WHERE deleted = FALSE ORDER BY code").stream()
                .map(payload -> codec.read(payload, SchemaApp.class))
                .toList();
    }

    @Override
    public void saveDraft(SchemaApp app) {
        String payload = codec.write(app);
        String updatedAt = app.updatedAt() == null ? "" : app.updatedAt();
        int updated = jdbc.update(
                "UPDATE daos_app SET code = ?, deleted = ?, updated_at = ?, payload = ? WHERE app_id = ?",
                app.code(), app.deleted(), updatedAt, payload, app.id());
        if (updated == 0) {
            jdbc.update("INSERT INTO daos_app (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?)",
                    app.id(), app.code(), app.deleted(), updatedAt, payload);
        }
    }

    private List<String> payloads(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }
}
