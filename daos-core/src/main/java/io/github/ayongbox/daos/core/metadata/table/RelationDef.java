package io.github.ayongbox.daos.core.metadata.table;

import io.github.ayongbox.daos.core.metadata.common.OnDeleteAction;
import io.github.ayongbox.daos.core.metadata.common.RelationType;

import java.util.Objects;

/**
 * 表间关联定义。多对多由平台隐式建中间表。
 */
public record RelationDef(
        String id,
        String code,
        String displayName,
        RelationType type,
        String sourceTableId,
        String targetTableId,
        String sourceFieldId,
        String targetFieldId,
        String joinTableCode,
        OnDeleteAction onDelete,
        OnDeleteAction onUpdate,
        boolean bidirectional,
        String reverseFieldId,
        String filterExpression,
        boolean enabled) {

    public RelationDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sourceTableId, "sourceTableId");
        Objects.requireNonNull(targetTableId, "targetTableId");
        onDelete = onDelete == null ? OnDeleteAction.RESTRICT : onDelete;
        onUpdate = onUpdate == null ? OnDeleteAction.RESTRICT : onUpdate;
    }
}
