package io.github.ayongbox.daos.core.metadata.table;

import io.github.ayongbox.daos.core.metadata.common.IndexType;

import java.util.List;
import java.util.Objects;

/**
 * 业务索引定义。
 */
public record IndexDef(
        String id,
        String name,
        List<String> columns,
        boolean unique,
        IndexType type,
        String where,
        String description) {

    public IndexDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("索引 " + name + " 至少需要一列");
        }
        if (columns.size() > 5) {
            throw new IllegalArgumentException("索引 " + name + " 最多 5 列");
        }
        columns = List.copyOf(columns);
        type = type == null ? IndexType.BTREE : type;
    }
}
