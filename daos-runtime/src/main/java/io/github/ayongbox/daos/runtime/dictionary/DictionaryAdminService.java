package io.github.ayongbox.daos.runtime.dictionary;

import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.dictionary.DictionaryItem;
import io.github.ayongbox.daos.core.metadata.dictionary.SchemaDictionary;
import io.github.ayongbox.daos.core.spi.DictionaryStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 字典管理。应用级字典的查询、覆盖写入与软删除，编码在应用内唯一。
 */
public final class DictionaryAdminService {

    private final DictionaryStore dictionaryStore;
    private final Clock clock;

    public DictionaryAdminService(DictionaryStore dictionaryStore) {
        this(dictionaryStore, Clock.systemUTC());
    }

    public DictionaryAdminService(DictionaryStore dictionaryStore, Clock clock) {
        this.dictionaryStore = Objects.requireNonNull(dictionaryStore, "dictionaryStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 返回应用下未删除的字典，按 code 排序。
     */
    public List<SchemaDictionary> list(String appId) {
        requireText(appId, "appId");
        return dictionaryStore.findByApp(appId).stream()
                .filter(dictionary -> !dictionary.deleted())
                .sorted(Comparator.comparing(SchemaDictionary::code))
                .toList();
    }

    /**
     * 按「应用 + 编码」覆盖写入字典（新建或替换，软删除的字典会被复活）。
     * {@code tree} / {@code enabled} 为 null 时沿用已有字典的取值，新建默认 false / true。
     */
    public SchemaDictionary upsert(
            String appId, String code, String operator,
            String name, String description, Boolean tree, Boolean enabled,
            List<DictionaryItem> items) {
        requireText(appId, "appId");
        requireText(code, "code");
        requireText(operator, "operator");
        requireText(name, "name");

        Optional<SchemaDictionary> existing = dictionaryStore.find(appId, code);
        boolean isTree = tree != null ? tree : existing.map(SchemaDictionary::tree).orElse(false);
        boolean isEnabled = enabled != null ? enabled : existing.map(SchemaDictionary::enabled).orElse(true);
        String now = now();
        SchemaDictionary dictionary = new SchemaDictionary(
                existing.map(SchemaDictionary::id).orElseGet(Ids::ulid),
                appId, code, name, description, isTree, isEnabled,
                items == null ? List.of() : items,
                existing.map(SchemaDictionary::ext).orElse(Map.of()),
                existing.map(SchemaDictionary::createdBy).orElse(operator),
                existing.map(SchemaDictionary::createdAt).orElse(now),
                operator, now, false, 1);
        dictionaryStore.save(dictionary);
        return dictionary;
    }

    /**
     * 软删除字典。字典不存在或已删除时拒绝。
     */
    public void softDelete(String appId, String code, String operator) {
        requireText(appId, "appId");
        requireText(code, "code");
        requireText(operator, "operator");
        SchemaDictionary existing = dictionaryStore.find(appId, code)
                .filter(dictionary -> !dictionary.deleted())
                .orElseThrow(() -> new IllegalArgumentException("字典不存在: " + code));
        String now = now();
        dictionaryStore.save(new SchemaDictionary(
                existing.id(), existing.appId(), existing.code(), existing.name(),
                existing.description(), existing.tree(), existing.enabled(), existing.items(),
                existing.ext(), existing.createdBy(), existing.createdAt(), operator, now,
                true, existing.schemaVersion()));
    }

    private String now() {
        return Instant.now(clock).toString();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
