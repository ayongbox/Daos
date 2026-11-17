package io.github.ayongbox.daos.runtime.engine.dialect;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * 方言实现的公共语法工具。只处理引号、字面量与标识符长度，不包含任何方言分支。
 */
final class DialectSupport {

    private DialectSupport() {
    }

    static String quote(String identifier, char quoteChar) {
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("标识符不能为空");
        }
        if (identifier.indexOf(quoteChar) >= 0) {
            throw new IllegalArgumentException("标识符包含引号字符: " + identifier);
        }
        return quoteChar + identifier + quoteChar;
    }

    /**
     * 参数 {@code booleanAsKeyword} 为 true 时布尔默认值渲染为 TRUE/FALSE，否则渲染为 1/0。
     */
    static String literal(Object value, boolean booleanAsKeyword) {
        Objects.requireNonNull(value, "value");
        if (value instanceof Boolean flag) {
            if (booleanAsKeyword) {
                return flag ? "TRUE" : "FALSE";
            }
            return flag ? "1" : "0";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return quoteLiteral(enumValue.name());
        }
        return quoteLiteral(String.valueOf(value));
    }

    static String quoteLiteral(String raw) {
        return "'" + raw.replace("'", "''") + "'";
    }

    /**
     * 超长标识符按确定性规则截断：保留前缀 + 原名的 CRC32 后缀，保证同名同结果。
     */
    static String truncate(String name, int maxLength) {
        if (name.length() <= maxLength) {
            return name;
        }
        CRC32 crc = new CRC32();
        crc.update(name.getBytes(StandardCharsets.UTF_8));
        String suffix = "_" + String.format("%08x", crc.getValue());
        return name.substring(0, maxLength - suffix.length()) + suffix;
    }
}
