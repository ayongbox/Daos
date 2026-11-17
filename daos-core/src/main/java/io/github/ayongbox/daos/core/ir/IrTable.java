package io.github.ayongbox.daos.core.ir;

import io.github.ayongbox.daos.core.metadata.table.SchemaTable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表的编译视图。保留原始元数据，并补充物理表名与列的物理映射决策。
 */
public record IrTable(
        SchemaTable source,
        String physicalName,
        List<IrColumn> columns,
        Map<String, IrColumn> columnsByFieldId,
        Map<String, IrColumn> columnsByCode) {

    public IrTable {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(physicalName, "physicalName");
        columns = columns == null ? List.of() : List.copyOf(columns);
        columnsByFieldId = columnsByFieldId == null ? Map.of() : Map.copyOf(columnsByFieldId);
        columnsByCode = columnsByCode == null ? Map.of() : Map.copyOf(columnsByCode);
    }

    /**
     * 默认归一化：所有字段映射为物理列，物理名等于字段 code。
     */
    public static IrTable of(SchemaTable source, String physicalName) {
        List<IrColumn> columns = source.fields().stream()
                .map(field -> new IrColumn(field, field.code(), IrColumn.Storage.COLUMN))
                .toList();
        Map<String, IrColumn> byId = new LinkedHashMap<>();
        Map<String, IrColumn> byCode = new LinkedHashMap<>();
        for (IrColumn column : columns) {
            byId.put(column.fieldId(), column);
            byCode.put(column.fieldCode(), column);
        }
        return new IrTable(source, physicalName, columns, byId, byCode);
    }

    public String tableId() {
        return source.id();
    }

    public String tableCode() {
        return source.code();
    }

    public IrColumn requireColumn(String fieldCode) {
        IrColumn column = columnsByCode.get(fieldCode);
        if (column == null) {
            throw new IllegalArgumentException("表 " + tableCode() + " 不存在字段: " + fieldCode);
        }
        return column;
    }

    public IrColumn columnByFieldId(String fieldId) {
        return columnsByFieldId.get(fieldId);
    }

    /**
     * 记录标题列，用于列表展示与关联引用。未配置时回退到第一个非派生文本列。
     */
    public IrColumn nameColumn() {
        String nameFieldId = source.nameFieldId();
        if (nameFieldId != null) {
            IrColumn column = columnsByFieldId.get(nameFieldId);
            if (column != null) {
                return column;
            }
        }
        return columns.stream()
                .filter(IrColumn::hasPhysicalColumn)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("表 " + tableCode() + " 没有可用的标题列"));
    }

    public List<IrColumn> physicalColumns() {
        return columns.stream()
                .filter(IrColumn::hasPhysicalColumn)
                .toList();
    }
}
