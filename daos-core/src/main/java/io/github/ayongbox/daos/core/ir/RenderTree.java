package io.github.ayongbox.daos.core.ir;

import io.github.ayongbox.daos.core.metadata.common.FormScene;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 渲染树。字段权限在编译期已裁剪，运行时不再做二次判断。
 */
public record RenderTree(
        String formId,
        FormScene scene,
        int schemaVersion,
        List<RenderNode> nodes) {

    public RenderTree {
        Objects.requireNonNull(formId, "formId");
        scene = scene == null ? FormScene.VIEW : scene;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * 渲染节点。容器与字段共用结构，children 为空表示叶子节点。
     */
    public record RenderNode(
            String id,
            String component,
            String fieldCode,
            String label,
            int span,
            boolean readonly,
            boolean hidden,
            Map<String, Object> props,
            List<RenderNode> children) {

        public RenderNode {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(component, "component");
            props = props == null ? Map.of() : Map.copyOf(props);
            children = children == null ? List.of() : List.copyOf(children);
        }

        public boolean isContainer() {
            return !children.isEmpty();
        }
    }
}
