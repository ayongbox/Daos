package io.github.ayongbox.daos.runtime.permission;

import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.metadata.common.MaskType;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.MaskRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.PatternSyntaxException;

/**
 * 字段脱敏。只处理已经过权限重写、字段已裁剪的查询结果，
 * 按字段编码就地替换值，不修改调用方传入的行对象。
 *
 * <p>隐藏字段不出现在投影中，因此这里只处理 {@link MaskRule}；未知字段直接跳过。</p>
 */
public final class FieldMasker {

    public List<Map<String, Object>> apply(
            IrTable table, FieldMask mask, List<Map<String, Object>> rows) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(rows, "rows");

        if (mask.maskRules().isEmpty() || rows.isEmpty()) {
            return rows;
        }

        Map<String, MaskRule> rulesByCode = rulesByCode(table, mask);
        if (rulesByCode.isEmpty()) {
            return rows;
        }

        List<Map<String, Object>> masked = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            rulesByCode.forEach((code, rule) -> {
                if (copy.containsKey(code)) {
                    copy.put(code, maskValue(copy.get(code), rule));
                }
            });
            masked.add(copy);
        }
        return masked;
    }

    private Map<String, MaskRule> rulesByCode(IrTable table, FieldMask mask) {
        Map<String, MaskRule> rules = new HashMap<>();
        for (MaskRule rule : mask.maskRules()) {
            if (mask.hidden(rule.fieldId())) {
                continue;
            }
            IrColumn column = table.columnByFieldId(rule.fieldId());
            if (column != null) {
                rules.putIfAbsent(column.fieldCode(), rule);
            }
        }
        return rules;
    }

    private Object maskValue(Object value, MaskRule rule) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return switch (rule.type()) {
            case PHONE -> keep(text, 3, 4);
            case ID_CARD -> keep(text, 3, 4);
            case EMAIL -> maskEmail(text);
            case NAME -> keep(text, 1, 0);
            case CUSTOM -> maskCustom(text, rule);
        };
    }

    private String keep(String text, int prefix, int suffix) {
        int length = text.length();
        if (length <= prefix + suffix) {
            return "*".repeat(Math.max(length, 1));
        }
        return text.substring(0, prefix)
                + "*".repeat(length - prefix - suffix)
                + text.substring(length - suffix);
    }

    private String maskEmail(String text) {
        int at = text.indexOf('@');
        if (at <= 0) {
            return keep(text, 1, 0);
        }
        return text.charAt(0) + "***" + text.substring(at);
    }

    private String maskCustom(String text, MaskRule rule) {
        String replacement = rule.replacement() == null ? "*" : rule.replacement();
        try {
            return text.replaceAll(rule.pattern(), replacement);
        } catch (PatternSyntaxException error) {
            throw new PermissionRewriteException(
                    "CUSTOM 脱敏规则 pattern 非法: " + rule.pattern(), error);
        }
    }
}
