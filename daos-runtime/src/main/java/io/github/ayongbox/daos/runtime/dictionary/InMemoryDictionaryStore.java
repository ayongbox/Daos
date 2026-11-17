package io.github.ayongbox.daos.runtime.dictionary;

import io.github.ayongbox.daos.core.metadata.dictionary.SchemaDictionary;
import io.github.ayongbox.daos.core.spi.DictionaryStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存字典仓储，按「应用 + 字典编码」覆盖写入。生产实现应落库并用唯一索引约束。
 */
public final class InMemoryDictionaryStore implements DictionaryStore {

    private final Map<String, SchemaDictionary> dictionaries = new ConcurrentHashMap<>();

    @Override
    public Optional<SchemaDictionary> find(String appId, String code) {
        return Optional.ofNullable(dictionaries.get(key(appId, code)));
    }

    @Override
    public List<SchemaDictionary> findByApp(String appId) {
        return dictionaries.values().stream()
                .filter(dictionary -> dictionary.appId().equals(appId))
                .toList();
    }

    @Override
    public void save(SchemaDictionary dictionary) {
        dictionaries.put(key(dictionary.appId(), dictionary.code()), dictionary);
    }

    private String key(String appId, String code) {
        return appId + ":" + code;
    }
}
