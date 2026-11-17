package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.metadata.common.DictSource;
import io.github.ayongbox.daos.core.metadata.dictionary.DictionaryItem;
import io.github.ayongbox.daos.core.metadata.dictionary.SchemaDictionary;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.OptionItem;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.runtime.dictionary.DictionaryCatalog;
import io.github.ayongbox.daos.runtime.dictionary.DictionaryReferenceException;
import io.github.ayongbox.daos.runtime.dictionary.InMemoryDictionaryStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictionaryCatalogTest {

    private static final String APP = "app1";

    private InMemoryDictionaryStore store;
    private DictionaryCatalog catalog;

    @BeforeEach
    void setUp() {
        store = new InMemoryDictionaryStore();
        catalog = new DictionaryCatalog(store);
    }

    @Test
    void listsEnabledItemsInDisplayOrder() {
        store.save(dictionary(false, true, List.of(
                item("i1", null, "paid", "已支付", 2, true),
                item("i2", null, "draft", "草稿", 1, true),
                item("i3", null, "closed", "已关闭", 3, false))));

        List<DictionaryItem> items = catalog.enabledItems(APP, "order_status");

        assertEquals(List.of("draft", "paid"), items.stream().map(DictionaryItem::code).toList());
    }

    @Test
    void rejectsDisabledDictionary() {
        store.save(dictionary(false, false, List.of()));

        assertThrows(DictionaryReferenceException.class,
                () -> catalog.require(APP, "order_status"));
    }

    @Test
    void rejectsUnknownDictionary() {
        assertThrows(DictionaryReferenceException.class,
                () -> catalog.require(APP, "missing"));
    }

    @Test
    void acceptsFieldReferencingExistingDictionary() {
        store.save(dictionary(false, true, List.of()));

        catalog.validateField(selectField(new FieldConfig.SelectConfig(
                DictSource.DICT, "order_status", List.of(), "dropdown", false, null)));
    }

    @Test
    void rejectsFieldReferencingUnknownDictionary() {
        SchemaField field = selectField(new FieldConfig.SelectConfig(
                DictSource.DICT, "missing", List.of(), "dropdown", false, null));

        DictionaryReferenceException error = assertThrows(
                DictionaryReferenceException.class, () -> catalog.validateField(field));
        assertTrue(error.getMessage().contains("不存在"));
    }

    @Test
    void rejectsDictionaryFieldWithoutCode() {
        SchemaField field = selectField(new FieldConfig.SelectConfig(
                DictSource.DICT, null, List.of(), "dropdown", false, null));

        assertThrows(DictionaryReferenceException.class, () -> catalog.validateField(field));
    }

    @Test
    void acceptsInlineOptions() {
        SchemaField field = selectField(new FieldConfig.SelectConfig(
                DictSource.INLINE, null,
                List.of(option("o1", "yes", "是")), "radio", false, null));

        catalog.validateField(field);
    }

    @Test
    void rejectsEmptyInlineOptions() {
        SchemaField field = selectField(new FieldConfig.SelectConfig(
                DictSource.INLINE, null, List.of(), "radio", false, null));

        assertThrows(DictionaryReferenceException.class, () -> catalog.validateField(field));
    }

    @Test
    void rejectsDuplicateInlineOptionCodes() {
        SchemaField field = selectField(new FieldConfig.SelectConfig(
                DictSource.INLINE, null,
                List.of(option("o1", "yes", "是"), option("o2", "yes", "是")),
                "radio", false, null));

        assertThrows(DictionaryReferenceException.class, () -> catalog.validateField(field));
    }

    @Test
    void validatesMultiSelectConfig() {
        store.save(dictionary(false, true, List.of()));

        catalog.validateField(selectField(new FieldConfig.MultiSelectConfig(
                DictSource.DICT, "order_status", List.of(), "tag", 0, false, null)));

        SchemaField invalid = selectField(new FieldConfig.MultiSelectConfig(
                DictSource.INLINE, null, List.of(), "tag", 0, false, null));
        assertThrows(DictionaryReferenceException.class, () -> catalog.validateField(invalid));
    }

    @Test
    void rejectsFlatDictionaryItemWithParent() {
        assertThrows(IllegalArgumentException.class,
                () -> dictionary(false, true, List.of(item("i1", "i2", "a", "A", 1, true))));
    }

    @Test
    void rejectsTreeItemWithMissingParent() {
        assertThrows(IllegalArgumentException.class,
                () -> dictionary(true, true, List.of(item("i1", "missing", "a", "A", 1, true))));
    }

    @Test
    void rejectsTreeCycle() {
        assertThrows(IllegalArgumentException.class, () -> dictionary(true, true, List.of(
                item("i1", "i2", "a", "A", 1, true),
                item("i2", "i1", "b", "B", 2, true))));
    }

    @Test
    void rejectsDuplicateItemCodes() {
        assertThrows(IllegalArgumentException.class, () -> dictionary(false, true, List.of(
                item("i1", null, "a", "A", 1, true),
                item("i2", null, "a", "B", 2, true))));
    }

    @Test
    void storeOverwritesDictionaryWithSameCode() {
        store.save(dictionary(false, true, List.of(item("i1", null, "a", "A", 1, true))));
        store.save(dictionary(false, false, List.of(item("i2", null, "b", "B", 1, true))));

        assertEquals(1, store.findByApp(APP).size());
        assertEquals("b", store.find(APP, "order_status").orElseThrow()
                .items().get(0).code());
    }

    private static SchemaDictionary dictionary(
            boolean tree, boolean enabled, List<DictionaryItem> items) {
        return new SchemaDictionary(
                "d1", APP, "order_status", "订单状态", null, tree, enabled, items,
                Map.of(), null, null, null, null, false, 1);
    }

    private static DictionaryItem item(
            String id, String parentId, String code, String label, int sort, boolean enabled) {
        return new DictionaryItem(
                id, parentId, code, label, code, null, null, sort, enabled, false, null);
    }

    private static OptionItem option(String id, String code, String label) {
        return new OptionItem(id, code, label, null, null, 0, true, false, null);
    }

    private static SchemaField selectField(FieldConfig config) {
        return new SchemaField(
                "f_status", APP, "t_order", "status", "状态", null,
                config.logicalType(), config, null, null, null, false, false, 0, null,
                Map.of(), null, null, null, null, false, 1);
    }
}
