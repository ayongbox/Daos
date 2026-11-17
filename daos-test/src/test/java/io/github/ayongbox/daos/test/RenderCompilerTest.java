package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.RenderTree;
import io.github.ayongbox.daos.core.ir.RenderTree.RenderNode;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.FormScene;
import io.github.ayongbox.daos.core.metadata.common.MaskType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
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
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.MaskRule;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.engine.render.DefaultRenderCompiler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderCompilerTest {

    private static final String APP_ID = "app-1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private final SchemaNormalizer normalizer = new SchemaNormalizer();
    private final DefaultRenderCompiler compiler = new DefaultRenderCompiler();

    @Test
    void compilesFieldNodesWithCodesAndLabels() {
        SchemaTable users = table("t1", "users", List.of(text("t1", "f1", "name")));
        SchemaForm form = form("form-1", "basic", "t1",
                List.of(field("n1", "f1", FormComponent.INPUT, null, false, false, List.of())));
        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 3L, List.of(users), List.of(form));

        RenderTree tree = compiler.compile(schema, "form-1", FormScene.EDIT, FieldMask.empty());

        assertEquals("form-1", tree.formId());
        assertEquals(FormScene.EDIT, tree.scene());
        assertEquals(1, tree.nodes().size());
        RenderNode node = tree.nodes().get(0);
        assertEquals("n1", node.id());
        assertEquals("input", node.component());
        assertEquals("name", node.fieldCode());
        assertEquals("name", node.label());
        assertEquals(24, node.span());
        assertFalse(node.readonly());
        assertFalse(node.required());
        assertTrue(node.children().isEmpty());
    }

    @Test
    void compileIsDeterministicForSameInput() {
        SchemaTable users = table("t1", "users", List.of(text("t1", "f1", "name")));
        SchemaForm form = form("form-1", "basic", "t1",
                List.of(field("n1", "f1", FormComponent.INPUT, "姓名", false, false, List.of())));
        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(users), List.of(form));

        RenderTree first = compiler.compile(schema, "form-1", FormScene.VIEW, FieldMask.empty());
        RenderTree second = compiler.compile(schema, "form-1", FormScene.VIEW, FieldMask.empty());

        assertEquals(first, second);
    }

    @Test
    void dropsHiddenFieldAndEmptyContainer() {
        SchemaTable users = table("t1", "users",
                List.of(text("t1", "f1", "name"), text("t1", "f2", "title")));
        FormField hiddenName = field("n1", "f1", FormComponent.INPUT, null, false, true, List.of());
        FormField group = field("g1", null, FormComponent.GROUP, "分组", false, false, List.of(hiddenName));
        FormField visible = field("n2", "f2", FormComponent.INPUT, null, false, false, List.of());
        SchemaForm form = form("form-1", "basic", "t1", List.of(group, visible));
        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(users), List.of(form));

        RenderTree tree = compiler.compile(schema, "form-1", FormScene.VIEW, FieldMask.empty());

        assertEquals(1, tree.nodes().size());
        assertEquals("n2", tree.nodes().get(0).id());
    }

    @Test
    void mergesReadonlyRequiredAndMaskFromPermission() {
        SchemaTable users = table("t1", "users",
                List.of(text("t1", "f1", "name"), text("t1", "f2", "phone")));
        SchemaForm form = form("form-1", "basic", "t1", List.of(
                field("n1", "f1", FormComponent.INPUT, null, false, false, List.of()),
                field("n2", "f2", FormComponent.INPUT, null, false, false, List.of())));
        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(users), List.of(form));
        FieldMask mask = new FieldMask(
                List.of(), List.of("f1"), List.of("f2"),
                List.of(new MaskRule("f2", MaskType.PHONE, null, null)));

        RenderTree tree = compiler.compile(schema, "form-1", FormScene.VIEW, mask);

        RenderNode name = tree.nodes().get(0);
        RenderNode phone = tree.nodes().get(1);
        assertTrue(name.readonly());
        assertFalse(name.required());
        assertTrue(phone.required());
        assertEquals("PHONE", phone.props().get("maskType"));
    }

    @Test
    void hidesFieldThroughPermissionMask() {
        SchemaTable users = table("t1", "users",
                List.of(text("t1", "f1", "name"), text("t1", "f2", "phone")));
        SchemaForm form = form("form-1", "basic", "t1", List.of(
                field("n1", "f1", FormComponent.INPUT, null, false, false, List.of()),
                field("n2", "f2", FormComponent.INPUT, null, false, false, List.of())));
        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(users), List.of(form));

        RenderTree tree = compiler.compile(
                schema, "form-1", FormScene.VIEW, new FieldMask(List.of("f2"), List.of(), List.of(), List.of()));

        assertEquals(1, tree.nodes().size());
        assertEquals("n1", tree.nodes().get(0).id());
        assertEquals("name", tree.nodes().get(0).fieldCode());
    }

    @Test
    void rendersSubformChildrenFromReferencedTable() {
        SchemaTable users = table("t1", "users", List.of(text("t1", "f1", "name")));
        SchemaTable items = table("t2", "items", List.of(text("t2", "f3", "amount")));
        FormField subform = new FormField(
                "s1", null, FormComponent.SUBFORM, "明细", 24, 0, 0, null, null, null,
                false, false, false, null, null, Map.of(), Map.of(), List.of(),
                "t2", List.of(field("a1", "f3", FormComponent.NUMBER, null, false, false, List.of())),
                1, 100, true, true, true, true, false, Map.of(), null, null, false, false, 2);
        SchemaForm form = form("form-1", "basic", "t1", List.of(subform));
        NormalizedSchema schema = normalizer.normalize(
                "demo", "snap-1", 1L, List.of(users, items), List.of(form));

        RenderTree tree = compiler.compile(schema, "form-1", FormScene.EDIT, FieldMask.empty());

        RenderNode node = tree.nodes().get(0);
        assertEquals("subform", node.component());
        assertEquals(1, node.children().size());
        assertEquals("amount", node.children().get(0).fieldCode());
    }

    @Test
    void rejectsUnknownForm() {
        SchemaTable users = table("t1", "users", List.of(text("t1", "f1", "name")));
        NormalizedSchema schema = normalizer.normalize("demo", "snap-1", 1L, List.of(users));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> compiler.compile(schema, "missing", FormScene.VIEW, FieldMask.empty()));

        assertTrue(error.getMessage().contains("未知表单"));
    }

    private static FormField field(
            String id,
            String fieldId,
            FormComponent component,
            String label,
            boolean readonly,
            boolean hidden,
            List<FormField> children) {
        return new FormField(
                id, fieldId, component, label, 24, 0, 0, null, null, null,
                readonly, hidden, false, null, null, Map.of(), Map.of(), children,
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

    private static SchemaTable table(String id, String code, List<SchemaField> fields) {
        return new SchemaTable(id, APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                code, code, null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields, List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
