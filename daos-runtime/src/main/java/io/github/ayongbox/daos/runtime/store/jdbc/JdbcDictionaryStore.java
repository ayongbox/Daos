package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.metadata.dictionary.SchemaDictionary;
import io.github.ayongbox.daos.core.spi.DictionaryStore;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 字典仓储的数据库实现。按「应用 + 字典编码」覆盖写入，软删除保留历史行。
 */
public final class JdbcDictionaryStore implements DictionaryStore {

    private final JdbcTemplate jdbc;
    private final JsonCodec codec;

    public JdbcDictionaryStore(JdbcTemplate jdbc, JsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public Optional<SchemaDictionary> find(String appId, String code) {
        return payloads("SELECT payload FROM daos_dictionary WHERE app_id = ? AND code = ?",
                appId, code)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, SchemaDictionary.class));
    }

    @Override
    public List<SchemaDictionary> findByApp(String appId) {
        return payloads("SELECT payload FROM daos_dictionary WHERE app_id = ?", appId).stream()
                .map(payload -> codec.read(payload, SchemaDictionary.class))
                .toList();
    }

    @Override
    public void save(SchemaDictionary dictionary) {
        String payload = codec.write(dictionary);
        int updated = jdbc.update(
                "UPDATE daos_dictionary SET deleted = ?, updated_at = ?, payload = ?"
                        + " WHERE app_id = ? AND code = ?",
                dictionary.deleted(), dictionary.updatedAt(), payload,
                dictionary.appId(), dictionary.code());
        if (updated == 0) {
            insert("INSERT INTO daos_dictionary (app_id, code, deleted, updated_at, payload)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    dictionary.appId(), dictionary.code(), dictionary.deleted(),
                    dictionary.updatedAt(), payload);
        }
    }

    private void insert(String sql, Object... args) {
        try {
            jdbc.update(sql, args);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("字典数据写入冲突", e);
        }
    }

    private List<String> payloads(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }
}
