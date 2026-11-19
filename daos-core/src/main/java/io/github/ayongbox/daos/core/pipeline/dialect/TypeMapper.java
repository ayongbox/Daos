package io.github.ayongbox.daos.core.pipeline.dialect;

import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;

/**
 * 逻辑类型到物理类型的映射。物理类型只允许出现在方言实现中，不进入元数据。
 */
public interface TypeMapper {

    /**
     * 业务字段的物理类型。无法获取所属表时使用，rollup 无法解析源字段时回退到默认数值类型。
     */
    String sqlType(SchemaField field);

    /**
     * 业务字段的物理类型（带表上下文）。rollup 的 max/min 需要根据源字段类型决定，
     * 其余类型与单参版本一致。
     */
    default String sqlType(SchemaField field, IrTable table) {
        return sqlType(field);
    }

    /** 带完整 Schema 上下文，用于解析 rollup 在子表中的来源字段。 */
    default String sqlType(SchemaField field, IrTable table, NormalizedSchema schema) {
        return sqlType(field, table);
    }

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
