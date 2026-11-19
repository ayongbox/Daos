package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.RenderTree.RenderNode;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.DictSource;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.FormScene;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.OptionItem;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.form.FormComponent;
import io.github.ayongbox.daos.core.metadata.form.FormField;
import io.github.ayongbox.daos.core.metadata.form.FormType;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.form.FormRenderService;
import io.github.ayongbox.daos.runtime.form.FormRenderService.FormRenderResult;
import io.github.ayongbox.daos.runtime.form.FormRenderService.FormSummary;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormRenderServiceTest {

    private static final String APP_ID = "app-1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private InMemoryMetadataStore metadataStore;
    private InMemorySnapshotStore snapshotStore;
    private PublishService publishService;
    private FormRenderService renderService;

    @BeforeEach
    void setUp() {
        metadataStore = new InMemoryMetadataStore();
        snapshotStore = new InMemorySnapshotStore();
        publishService = new PublishService(metadataStore, snapshotStore);
        renderService = new FormRenderService(snapshotStore);
    }

    @Test
    void listsAndRendersFormFromActiveSnapshot() {
        metadataStore.saveDraft(app("客户表单", List.of(text("f1", "name"))));
        publishService.publish(APP_ID, "alice");

        List<FormSummary> forms = renderService.listForms(APP_ID);

        assertEquals(1, forms.size());
        assertEquals("basic", forms.get(0).formCode());
        assertEquals("t1", forms.get(0).tableId());
        assertEquals("users", forms.get(0).tableCode());

        FormRenderResult result = renderService.render(APP_ID, "basic", FormScene.CREATE);

        assertEquals("users", result.tableCode());
        assertEquals("CREATE", result.scene());
        assertEquals(1, result.tree().nodes().size());
        RenderNode node = result.tree().nodes().get(0);
        assertEquals("name", node.fieldCode());
        assertEquals("input", node.component());
    }

    @Test
    void rejectsWhenAppHasNoActiveRelease() {
        metadataStore.saveDraft(app("客户表单", List.of(text("f1", "name"))));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> renderService.listForms(APP_ID));

        assertTrue(error.getMessage().contains("还没有生效版本"));
    }

    @Test
    void rejectsUnknownFormCode() {
        metadataStore.saveDraft(app("客户表单", List.of(text("f1", "name"))));
        publishService.publish(APP_ID, "alice");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> renderService.render(APP_ID, "missing", FormScene.CREATE));

        assertTrue(error.getMessage().contains("未知表单"));
    }

    @Test
    void formOnlyChangeProducesNewRelease() {
        metadataStore.saveDraft(app("客户表单", List.of(text("f1", "name"))));
        SchemaSnapshot first = publishService.publish(APP_ID, "alice");

        metadataStore.saveDraft(app("客户表单 v2", List.of(text("f1", "name"))));
        SchemaSnapshot second = publishService.publish(APP_ID, "alice");

        assertTrue(second.releaseVersion() > first.releaseVersion());
        assertEquals("客户表单 v2", renderService.listForms(APP_ID).get(0).displayName());
    }

    @Test
    void selectFieldRendersInlineOptions() {
        metadataStore.saveDraft(new SchemaApp(
                APP_ID, "demo", "客户应用", null, null, MetadataStatus.DRAFT,
                List.of(table(List.of(text("f1", "name"), select("f2", "status")))),
                List.of(form("客户表单", List.of(
                        field("n1", "f1", FormComponent.INPUT, "姓名"),
                        field("n2", "f2", FormComponent.SELECT, "状态")))),
                List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1));
        publishService.publish(APP_ID, "alice");

        FormRenderResult result = renderService.render(APP_ID, "basic", FormScene.CREATE);

        RenderNode node = result.tree().nodes().get(1);
        assertEquals("select", node.component());
        assertEquals(List.of(
                Map.of("label", "正常", "value", "normal"),
                Map.of("label", "已取消", "value", "cancelled")), node.props().get("options"));
    }

    private static SchemaApp app(String formDisplayName, List<SchemaField> fields) {
        return new SchemaApp(
                APP_ID, "demo", "客户应用", null, null, MetadataStatus.DRAFT,
                List.of(table(fields)), List.of(form(formDisplayName)), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaForm form(String displayName) {
        return form(displayName, List.of(field("n1", "f1", FormComponent.INPUT, "姓名")));
    }

    private static SchemaForm form(String displayName, List<FormField> fields) {
        return new SchemaForm(
                "form-1", APP_ID, "t1", "basic", displayName, null, FormType.RECORD, Map.of(),
                fields,
                List.of(), List.of(), List.of(), MetadataStatus.DRAFT, 1, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static FormField field(String id, String fieldId, FormComponent component, String label) {
        return new FormField(
                id, fieldId, component, label, 24, 0, 0, null, null, null,
                false, false, false, null, null, Map.of(), Map.of(), List.of(),
                null, List.of(), 0, 0, true, true, true, true, false, Map.of(),
                null, null, false, false, 2);
    }

    private static SchemaField text(String id, String code) {
        return new SchemaField(id, APP_ID, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.DRAFT, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField select(String id, String code) {
        return new SchemaField(id, APP_ID, "t1", code, code, null,
                FieldLogicalType.SELECT,
                new FieldConfig.SelectConfig(DictSource.INLINE, null,
                        List.of(new OptionItem("o1", "normal", "正常", null, null, 0, true, true, null),
                                new OptionItem("o2", "cancelled", "已取消", null, null, 1, true, false, null)),
                        "TAG", false, "AUTO"),
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.DRAFT, false, false, 1, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaTable table(List<SchemaField> fields) {
        return new SchemaTable("t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "客户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.DRAFT, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields, List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
