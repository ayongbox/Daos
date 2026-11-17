package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.SubjectType;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.form.FormType;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.resource.PermissionPoint;
import io.github.ayongbox.daos.core.metadata.resource.ResourceActionCatalog;
import io.github.ayongbox.daos.core.metadata.resource.ResourceNode;
import io.github.ayongbox.daos.core.metadata.resource.ResourceTree;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCatalogTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    @Test
    void acceptsDocumentedResourceActionPairs() {
        assertTrue(ResourceActionCatalog.supports(ResourceType.APP, ActionType.PUBLISH));
        assertTrue(ResourceActionCatalog.supports(ResourceType.TABLE, ActionType.SHARE));
        assertTrue(ResourceActionCatalog.supports(ResourceType.FLOW, ActionType.START));
        assertTrue(ResourceActionCatalog.supports(ResourceType.FIELD, ActionType.WRITE));
        assertTrue(ResourceActionCatalog.supports(ResourceType.MENU, ActionType.VIEW));
        assertTrue(ResourceActionCatalog.supports(ResourceType.FILE, ActionType.DOWNLOAD));
    }

    @Test
    void rejectsUndocumentedResourceActionPairs() {
        assertFalse(ResourceActionCatalog.supports(ResourceType.TABLE, ActionType.PUBLISH));
        assertFalse(ResourceActionCatalog.supports(ResourceType.MENU, ActionType.CREATE));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ResourceActionCatalog.require(ResourceType.TABLE, ActionType.PUBLISH));

        assertTrue(error.getMessage().contains("不支持动作"));
    }

    @Test
    void registersUniquePermissionPointCodes() {
        List<PermissionPoint> points = ResourceActionCatalog.points();
        Set<String> codes = new HashSet<>();
        for (PermissionPoint point : points) {
            assertTrue(codes.add(point.code()), "重复权限点: " + point.code());
            assertEquals(point, ResourceActionCatalog.requirePoint(point.code()));
        }
        assertEquals(ActionType.READ, ResourceActionCatalog.requirePoint("table:read").action());
        assertThrows(IllegalArgumentException.class,
                () -> ResourceActionCatalog.requirePoint("table:publish"));
    }

    @Test
    void schemaPermissionRejectsIllegalAction() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> permission("bad", ResourceType.TABLE, List.of(ActionType.PUBLISH)));

        assertTrue(error.getMessage().contains("不支持动作"));
    }

    @Test
    void buildsResourceTreeFromAppMetadata() {
        SchemaField name = field("f1", "name", false);
        SchemaField createdAt = field("f2", "created_at", true);
        SchemaTable users = table("t1", "users", "用户", List.of(name, createdAt));
        SchemaForm form = form("form-1", "basic", "t1", "用户表单");

        SchemaApp app = new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.PUBLISHED,
                List.of(users), List.of(form), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        ResourceTree tree = ResourceTree.of(app);

        assertEquals(ResourceType.APP, tree.root().type());
        assertEquals(APP_CODE, tree.root().code());
        assertEquals(2, tree.root().children().size());
        assertTrue(tree.find(ResourceType.TABLE, "users").isPresent());
        assertTrue(tree.find(ResourceType.FIELD, "name").isPresent());
        assertTrue(tree.find(ResourceType.FORM, "basic").isPresent());
        assertTrue(tree.find(ResourceType.FIELD, "created_at").isEmpty());
    }

    private static SchemaPermission permission(String code, ResourceType type, List<ActionType> actions) {
        return new SchemaPermission(
                "p-" + code, APP_ID, code, code, null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(type, APP_CODE, "users", List.of()),
                actions, EffectType.ALLOW, null, null, null,
                100, true, null, null, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField field(String id, String code, boolean system) {
        return new SchemaField(
                id, APP_ID, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, system, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaTable table(String id, String code, String displayName, List<SchemaField> fields) {
        return new SchemaTable(id, APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                code, displayName, null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields, List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaForm form(String id, String code, String tableId, String displayName) {
        return new SchemaForm(
                id, APP_ID, tableId, code, displayName, null, FormType.RECORD, Map.of(),
                List.of(), List.of(), List.of(), List.of(), MetadataStatus.PUBLISHED, 1, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
