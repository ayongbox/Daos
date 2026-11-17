package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.OnDeleteAction;
import io.github.ayongbox.daos.core.metadata.common.RelationType;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.form.FormComponent;
import io.github.ayongbox.daos.core.metadata.form.FormField;
import io.github.ayongbox.daos.core.metadata.form.FormType;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizeException;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaNormalizerTest {

    private static final String APP_ID = "app-1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private final SchemaNormalizer normalizer = new SchemaNormalizer();

    @Test
    void buildsPhysicalNamesAndKeepsBothIndexes() {
        SchemaTable users = table("t1", "users", List.of(text("t1", "f1", "name")), List.of());

        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(users));

        assertEquals("demo", schema.appCode());
        assertEquals("app-1", schema.appId());
        assertEquals("app_demo_users", schema.requireTable("t1").physicalName());
        assertEquals("t1", schema.requireTableByCode("users").tableId());
        assertEquals(IrColumn.Storage.COLUMN,
                schema.requireTable("t1").requireColumn("name").storage());
    }

    @Test
    void marksComputedFieldsAsDerived() {
        SchemaTable orders = table("t1", "orders",
                List.of(text("t1", "f1", "amount"), formula("t1", "f2", "total", "{amount} * 2")),
                List.of());

        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(orders));

        assertEquals(IrColumn.Storage.DERIVED,
                schema.requireTable("t1").requireColumn("total").storage());
    }

    @Test
    void ordersDerivedFieldsByDependencyNotDeclarationOrder() {
        SchemaField base = text("t1", "f1", "amount");
        SchemaField middle = formula("t1", "f2", "with_tax", "{amount} * 1.13");
        SchemaField last = formula("t1", "f3", "total", "{with_tax} + 10");
        SchemaTable orders = table("t1", "orders", List.of(last, middle, base), List.of());

        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(orders));

        assertEquals(List.of("f2", "f3"), schema.derivedFieldOrder());
    }

    @Test
    void rejectsCyclicFormulaDependencies() {
        SchemaField left = formula("t1", "f1", "left", "{right} + 1");
        SchemaField right = formula("t1", "f2", "right", "{left} + 1");
        SchemaTable orders = table("t1", "orders", List.of(left, right), List.of());

        SchemaNormalizeException error = assertThrows(SchemaNormalizeException.class,
                () -> normalizer.normalize("demo", "snap-1", 1L, List.of(orders)));

        assertTrue(error.getMessage().contains("循环依赖"));
    }

    @Test
    void rejectsRelationToUnknownTable() {
        RelationDef relation = new RelationDef(
                "r1", "rel_customer", "关联客户", RelationType.MANY_TO_ONE,
                "t1", "missing", null, null, null,
                OnDeleteAction.RESTRICT, OnDeleteAction.RESTRICT, false, null, null, true);
        SchemaTable orders = table("t1", "orders", List.of(text("t1", "f1", "no")), List.of(relation));

        assertThrows(SchemaNormalizeException.class,
                () -> normalizer.normalize("demo", "snap-1", 1L, List.of(orders)));
    }

    @Test
    void rejectsDuplicateFieldCode() {
        SchemaTable orders = table("t1", "orders",
                List.of(text("t1", "f1", "amount"), text("t1", "f2", "amount")), List.of());

        assertThrows(SchemaNormalizeException.class,
                () -> normalizer.normalize("demo", "snap-1", 1L, List.of(orders)));
    }

    @Test
    void rejectsOversizedPhysicalName() {
        SchemaTable table = table("t1", "t".repeat(40), List.of(text("t1", "f1", "name")), List.of());

        assertThrows(SchemaNormalizeException.class,
                () -> normalizer.normalize("a".repeat(32), "snap-1", 1L, List.of(table)));
    }

    @Test
    void marksHybridColumnsByStorageRole() {
        SchemaField promoted = text("t1", "f1", "amount");
        SchemaField nested = text("t1", "f2", "memo");
        SchemaTable orders = table("t1", "orders", StorageMode.HYBRID, List.of("amount"),
                List.of(promoted, nested), List.of());

        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(orders));

        assertEquals(IrColumn.Storage.PROMOTED_JSON,
                schema.requireTable("t1").requireColumn("amount").storage());
        assertEquals(IrColumn.Storage.JSON_MEMBER,
                schema.requireTable("t1").requireColumn("memo").storage());
        assertEquals(1, schema.requireTable("t1").physicalColumns().size());
    }

    @Test
    void marksAllFieldsAsJsonMembersInJsonMode() {
        SchemaTable orders = table("t1", "orders", StorageMode.JSON, List.of(),
                List.of(text("t1", "f1", "amount"), text("t1", "f2", "memo")), List.of());

        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(orders));

        assertEquals(IrColumn.Storage.JSON_MEMBER,
                schema.requireTable("t1").requireColumn("amount").storage());
        assertEquals(0, schema.requireTable("t1").physicalColumns().size());
    }

    @Test
    void indexesFormsAndResolvesTableCode() {
        SchemaTable users = table("t1", "users", List.of(text("t1", "f1", "name")), List.of());
        SchemaForm form = form("form-1", "basic", "t1",
                List.of(formField("n1", "f1", FormComponent.INPUT, List.of())));

        NormalizedSchema schema = normalizer.normalize(
                "demo", "snap-1", 1L, List.of(users), List.of(form));

        assertEquals("users", schema.requireForm("form-1").tableCode());
        assertEquals("t1", schema.requireFormByCode("basic").tableId());
    }

    @Test
    void rejectsFormFieldNotBelongingToTable() {
        SchemaTable users = table("t1", "users", List.of(text("t1", "f1", "name")), List.of());
        SchemaForm form = form("form-1", "basic", "t1",
                List.of(formField("n1", "f9", FormComponent.INPUT, List.of())));

        SchemaNormalizeException error = assertThrows(SchemaNormalizeException.class,
                () -> normalizer.normalize("demo", "snap-1", 1L, List.of(users), List.of(form)));

        assertTrue(error.getMessage().contains("不存在的字段"));
    }

    @Test
    void rejectsFormPointingToMissingTable() {
        SchemaTable users = table("t1", "users", List.of(text("t1", "f1", "name")), List.of());
        SchemaForm form = form("form-1", "basic", "t9", List.of());

        assertThrows(SchemaNormalizeException.class,
                () -> normalizer.normalize("demo", "snap-1", 1L, List.of(users), List.of(form)));
    }

    private static FormField formField(
            String id, String fieldId, FormComponent component, List<FormField> children) {
        return new FormField(
                id, fieldId, component, null, 24, 0, 0, null, null, null,
                false, false, false, null, null, Map.of(), Map.of(), children,
                null, List.of(), 0, 0, true, true, true, true, false, Map.of(),
                null, null, false, false, 2);
    }

    private static SchemaForm form(String id, String code, String tableId, List<FormField> fields) {
        return new SchemaForm(
                id, APP_ID, tableId, code, code, null, FormType.RECORD, Map.of(),
                fields, List.of(), List.of(), List.of(), MetadataStatus.PUBLISHED, 1, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField text(String tableId, String id, String code) {
        return new SchemaField(id, APP_ID, tableId, code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField formula(String tableId, String id, String code, String expression) {
        return new SchemaField(id, APP_ID, tableId, code, code, null,
                FieldLogicalType.FORMULA,
                new FieldConfig.FormulaConfig(expression, "number", 2, "REALTIME"),
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaTable table(
            String id, String code, List<SchemaField> fields, List<RelationDef> relations) {
        return table(id, code, StorageMode.PHYSICAL, List.of(), fields, relations);
    }

    private static SchemaTable table(
            String id,
            String code,
            StorageMode storageMode,
            List<String> promotedColumns,
            List<SchemaField> fields,
            List<RelationDef> relations) {
        return new SchemaTable(id, APP_ID, TableOwnership.APPLICATION, false,
                storageMode, promotedColumns, null,
                code, code, null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields, List.of(), relations, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
