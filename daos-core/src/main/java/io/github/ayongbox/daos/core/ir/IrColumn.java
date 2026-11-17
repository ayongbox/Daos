package io.github.ayongbox.daos.core.ir;

import io.github.ayongbox.daos.core.metadata.field.SchemaField;

import java.util.Objects;

/**
 * 列的编译视图。物理类型由方言渲染器决定，IR 只表达存储归属。
 */
public record IrColumn(
        SchemaField source,
        String physicalName,
        Storage storage) {

    public enum Storage {
        /** 真实物理列，可索引、可过滤、可排序。 */
        COLUMN,
        /** 编译期派生，无独立物理列，禁止出现在 WHERE 与 ORDER BY。 */
        DERIVED,
        /** 混合存储下从 JSON 提升出的列，可过滤与排序，但禁止区间扫描。 */
        PROMOTED_JSON,
        /** 存放在 _ext json 列中，不可过滤、不可排序、不可建索引。 */
        JSON_MEMBER
    }

    public IrColumn {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(physicalName, "physicalName");
        storage = storage == null ? Storage.COLUMN : storage;
    }

    public String fieldId() {
        return source.id();
    }

    public String fieldCode() {
        return source.code();
    }

    public boolean filterable() {
        return hasPhysicalColumn() && source.constraints().filterable();
    }

    public boolean sortable() {
        return hasPhysicalColumn() && source.constraints().sortable();
    }

    /**
     * 是否拥有可直接引用的物理列。派生列与 JSON 成员都不满足。
     */
    public boolean hasPhysicalColumn() {
        return storage == Storage.COLUMN || storage == Storage.PROMOTED_JSON;
    }
}
