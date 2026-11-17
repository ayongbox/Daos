package io.github.ayongbox.daos.core.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 归一化 Schema。三路编译器的唯一输入，编译器不得回查原始元数据仓库。
 *
 * <p>归一化阶段负责：展开外部只读引用、解析默认值与继承、校验配额上限、
 * 确定物理表名与存储模式、对派生字段做拓扑排序。</p>
 */
public record NormalizedSchema(
        String appId,
        String appCode,
        String snapshotId,
        long releaseVersion,
        Map<String, IrTable> tablesById,
        Map<String, IrTable> tablesByCode,
        List<String> derivedFieldOrder) {

    public NormalizedSchema {
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(appCode, "appCode");
        // 保留声明顺序：DDL 必须逐字节可复现，不能依赖 Map.copyOf 的未定义迭代顺序。
        tablesById = preserveOrder(tablesById);
        tablesByCode = preserveOrder(tablesByCode);
        derivedFieldOrder = derivedFieldOrder == null ? List.of() : List.copyOf(derivedFieldOrder);
    }

    public IrTable requireTable(String tableId) {
        IrTable table = tablesById.get(tableId);
        if (table == null) {
            throw new IllegalArgumentException("未知表: " + tableId);
        }
        return table;
    }

    public IrTable requireTableByCode(String tableCode) {
        IrTable table = tablesByCode.get(tableCode);
        if (table == null) {
            throw new IllegalArgumentException("未知表编码: " + tableCode);
        }
        return table;
    }

    public boolean isDerived(String fieldId) {
        return derivedFieldOrder.contains(fieldId);
    }

    private static Map<String, IrTable> preserveOrder(Map<String, IrTable> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
