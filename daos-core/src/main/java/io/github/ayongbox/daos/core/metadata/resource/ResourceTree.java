package io.github.ayongbox.daos.core.metadata.resource;

import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 应用资源树。由应用元数据确定性构建，同一份元数据产出同一棵树。
 */
public record ResourceTree(ResourceNode root) {

    public ResourceTree {
        Objects.requireNonNull(root, "root");
    }

    /**
     * 构建资源树：应用为根，表与表单为子节点，非系统字段挂在所属表下。
     */
    public static ResourceTree of(SchemaApp app) {
        Objects.requireNonNull(app, "app");

        List<ResourceNode> children = new ArrayList<>(app.tables().size() + app.forms().size());
        for (SchemaTable table : app.tables()) {
            children.add(tableNode(table));
        }
        for (SchemaForm form : app.forms()) {
            children.add(new ResourceNode(ResourceType.FORM, form.code(), form.displayName(), List.of()));
        }

        ResourceNode root = new ResourceNode(
                ResourceType.APP, app.code(), app.displayName(), children);
        return new ResourceTree(root);
    }

    public Optional<ResourceNode> find(ResourceType type, String code) {
        return root.find(type, code);
    }

    private static ResourceNode tableNode(SchemaTable table) {
        List<ResourceNode> fields = new ArrayList<>();
        for (SchemaField field : table.fields()) {
            if (!field.system()) {
                fields.add(new ResourceNode(
                        ResourceType.FIELD, field.code(), field.displayName(), List.of()));
            }
        }
        return new ResourceNode(ResourceType.TABLE, table.code(), table.displayName(), fields);
    }
}
