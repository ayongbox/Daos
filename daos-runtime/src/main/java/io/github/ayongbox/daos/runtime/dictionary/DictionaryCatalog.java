package io.github.ayongbox.daos.runtime.dictionary;

import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.DictSource;
import io.github.ayongbox.daos.core.metadata.dictionary.DictionaryItem;
import io.github.ayongbox.daos.core.metadata.dictionary.SchemaDictionary;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.OptionItem;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.spi.DictionaryStore;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 字典读取与引用校验。字段的选项来源分为字典引用与内联选项两种，
 * 发布前必须保证引用存在、字典启用、内联选项可用。
 */
public final class DictionaryCatalog {

    private final DictionaryStore dictionaryStore;

    public DictionaryCatalog(DictionaryStore dictionaryStore) {
        this.dictionaryStore = Objects.requireNonNull(dictionaryStore, "dictionaryStore");
    }

    /**
     * 取启用中的字典，不存在或已停用时显式失败。
     */
    public SchemaDictionary require(String appId, String code) {
        SchemaDictionary dictionary = dictionaryStore.find(appId, code)
                .orElseThrow(() -> new DictionaryReferenceException(
                        "字典不存在: " + appId + "." + code));
        if (dictionary.deleted() || !dictionary.enabled()) {
            throw new DictionaryReferenceException("字典已停用或删除: " + appId + "." + code);
        }
        return dictionary;
    }

    /**
     * 启用中的字典项，按展示顺序返回；树形结构由调用方按 parentId 组装。
     */
    public List<DictionaryItem> enabledItems(String appId, String code) {
        return require(appId, code).items().stream()
                .filter(DictionaryItem::enabled)
                .sorted(Comparator.comparingInt(DictionaryItem::sortOrder)
                        .thenComparing(DictionaryItem::code))
                .toList();
    }

    public void validateApp(SchemaApp app) {
        Objects.requireNonNull(app, "app");
        for (SchemaTable table : app.tables()) {
            for (SchemaField field : table.fields()) {
                if (!field.deleted()) {
                    validateField(field);
                }
            }
        }
    }

    public void validateField(SchemaField field) {
        Objects.requireNonNull(field, "field");
        FieldConfig config = field.config();
        if (config instanceof FieldConfig.SelectConfig select) {
            validateOptions(field, select.source(), select.dictCode(), select.options());
        } else if (config instanceof FieldConfig.MultiSelectConfig multi) {
            validateOptions(field, multi.source(), multi.dictCode(), multi.options());
        }
    }

    private void validateOptions(
            SchemaField field, DictSource source, String dictCode, List<OptionItem> options) {
        if (source == null) {
            throw new DictionaryReferenceException(
                    "字段 " + field.code() + " 未指定选项来源");
        }

        if (source == DictSource.DICT) {
            if (dictCode == null || dictCode.isBlank()) {
                throw new DictionaryReferenceException(
                        "字段 " + field.code() + " 使用字典选项但未指定 dictCode");
            }
            require(field.appId(), dictCode);
            return;
        }

        List<OptionItem> inline = options == null ? List.of() : options;
        if (inline.isEmpty()) {
            throw new DictionaryReferenceException(
                    "字段 " + field.code() + " 使用内联选项但未配置任何选项");
        }
        Set<String> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (OptionItem option : inline) {
            if (option.code().isBlank() || option.label().isBlank()) {
                throw new DictionaryReferenceException(
                        "字段 " + field.code() + " 的选项编码与名称不能为空");
            }
            if (!ids.add(option.id())) {
                throw new DictionaryReferenceException(
                        "字段 " + field.code() + " 存在重复选项 id: " + option.id());
            }
            if (!codes.add(option.code())) {
                throw new DictionaryReferenceException(
                        "字段 " + field.code() + " 存在重复选项编码: " + option.code());
            }
        }
    }
}
