package io.github.ayongbox.daos.core.spi;

import io.github.ayongbox.daos.core.metadata.dictionary.SchemaDictionary;

import java.util.List;
import java.util.Optional;

/**
 * 数据字典仓储。字典以应用为隔离边界，同一应用内字典编码唯一。
 */
public interface DictionaryStore {

    Optional<SchemaDictionary> find(String appId, String code);

    List<SchemaDictionary> findByApp(String appId);

    /**
     * 按「应用 + 字典编码」覆盖写入。重复保存视为同一字典的新版本。
     */
    void save(SchemaDictionary dictionary);
}
