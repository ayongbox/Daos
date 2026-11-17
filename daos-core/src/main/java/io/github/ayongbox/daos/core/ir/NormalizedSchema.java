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
        Map<String, IrForm> formsById,
        Map<String, IrForm> formsByCode,
        List<String> derivedFieldOrder) {

    public NormalizedSchema {
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(appCode, "appCode");
        // 保留声明顺序：DDL 必须逐字节可复现，不能依赖 Map.copyOf 的未定义迭代顺序。
        tablesById = preserveOrder(tablesById);
        tablesByCode = preserveOrder(tablesByCode);
        formsById = preserveOrder(formsById);
        formsByCode = preserveOrder(formsByCode);
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

    public IrForm requireForm(String formId) {
        IrForm form = formsById.get(formId);
        if (form == null) {
            throw new IllegalArgumentException("未知表单: " + formId);
        }
        return form;
    }

    public IrForm requireFormByCode(String formCode) {
        IrForm form = formsByCode.get(formCode);
        if (form == null) {
            throw new IllegalArgumentException("未知表单编码: " + formCode);
        }
        return form;
    }

    public boolean isDerived(String fieldId) {
        return derivedFieldOrder.contains(fieldId);
    }

    private static <T> Map<String, T> preserveOrder(Map<String, T> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
