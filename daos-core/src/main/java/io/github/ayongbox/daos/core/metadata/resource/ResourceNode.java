package io.github.ayongbox.daos.core.metadata.resource;

import io.github.ayongbox.daos.core.metadata.common.ResourceType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 资源树节点。应用是根，表、表单等挂在应用下；字段作为表的子节点展示。
 *
 * <p>本树用于授权界面与资源导航；行级与字段级裁决仍以 {@code ResourceRef} 为唯一键。</p>
 */
public record ResourceNode(
        ResourceType type,
        String code,
        String displayName,
        List<ResourceNode> children) {

    public ResourceNode {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");
        children = children == null ? List.of() : List.copyOf(children);
    }

    /**
     * 深度优先查找自身或后代节点，编码需完全一致。
     */
    public Optional<ResourceNode> find(ResourceType type, String code) {
        if (this.type == type && this.code.equals(code)) {
            return Optional.of(this);
        }
        for (ResourceNode child : children) {
            Optional<ResourceNode> found = child.find(type, code);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
