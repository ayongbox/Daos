package io.github.ayongbox.daos.core.pipeline.dialect;

import io.github.ayongbox.daos.core.metadata.field.SchemaField;

/**
 * 逻辑类型到物理类型的映射。物理类型只允许出现在方言实现中，不进入元数据。
 */
public interface TypeMapper {

    /**
     * 业务字段的物理类型。派生字段（formula / lookup / rollup）没有物理列，调用即视为契约错误。
     */
    String sqlType(SchemaField field);

    /**
     * 平台自动列的物理类型。
     */
    String autoColumnType(AutoColumn column);

    enum AutoColumn {
        ID,
        TENANT_ID,
        CREATED_BY,
        CREATED_AT,
        UPDATED_BY,
        UPDATED_AT,
        VERSION,
        DELETED,
        EXT
    }
}
