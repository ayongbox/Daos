package io.github.ayongbox.daos.runtime.formula;

import io.github.ayongbox.daos.core.metadata.field.FieldConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 公式字段求值器。首版只支持标量算术（{@code + - * /}、括号、一元正负）与
 * {@code ROUND(x, 小数位)}，字段引用用 <code>{fieldCode}</code> 标记。
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li>不引入 JS 沙箱（那是 F04-01 的范围），自研递归下降解析器，字段引用白名单化防止注入；</li>
 *   <li>缺失操作数或除零返回 {@code null}（而非抛异常），避免单行脏数据阻断整批写入；</li>
 *   <li>表达式语法非法（无法解析、未知函数）抛 {@link IllegalArgumentException}，属于配置错误，
 *       应在规范化/设计期暴露；</li>
 *   <li>求值结果按 {@link FieldConfig.FormulaConfig#resultType()} 转换，与物理列类型对齐。</li>
 * </ul>
 */
public final class FormulaEvaluator {

    private static final int DEFAULT_DECIMAL_SCALE = 6;

    public Object evaluate(FieldConfig.FormulaConfig config, Map<String, Object> rowByCode) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(rowByCode, "rowByCode");
        String expression = config.expression();
        if (expression == null || expression.isBlank()) {
            return null;
        }
        BigDecimal value = new Parser(expression, rowByCode).parse();
        return convert(value, config.resultType(), config.precision());
    }

    private Object convert(BigDecimal value, String resultType, int precision) {
        if (value == null) {
            return null;
        }
        String type = resultType == null ? "text" : resultType.toLowerCase();
        return switch (type) {
            case "number", "integer", "int", "long" -> value.setScale(0, RoundingMode.HALF_UP).longValue();
            case "decimal", "double", "float" -> value.setScale(
                    precision > 0 ? precision : 2, RoundingMode.HALF_UP);
            case "boolean", "bool" -> value.compareTo(BigDecimal.ZERO) != 0;
            case "date" -> {
                // 数字公式转日期没有业务意义；保留为 null，避免写入非法值。
                yield null;
            }
            default -> value.stripTrailingZeros().toPlainString();
        };
    }

    /** 仅供测试/诊断：直接对表达式求值，返回 BigDecimal。 */
    BigDecimal evalRaw(String expression, Map<String, Object> rowByCode) {
        return new Parser(expression, rowByCode).parse();
    }

    private static final class Parser {

        private final String source;
        private final Map<String, Object> row;
        private final List<Token> tokens;
        private int cursor;

        Parser(String source, Map<String, Object> row) {
            this.source = source;
            this.row = row;
            this.tokens = tokenize(source);
            this.cursor = 0;
        }

        BigDecimal parse() {
            BigDecimal value = parseExpression();
            Token trailing = peek();
            if (trailing != null) {
                throw new IllegalArgumentException(
                        "公式表达式存在多余内容，位置 " + trailing.start() + ": " + source);
            }
            return value;
        }

        BigDecimal parseExpression() {
            BigDecimal value = parseTerm();
            while (match(TokenType.PLUS, TokenType.MINUS)) {
                Token op = previous();
                BigDecimal right = parseTerm();
                value = apply(value, op.type(), right);
            }
            return value;
        }

        private BigDecimal parseTerm() {
            BigDecimal value = parseFactor();
            while (match(TokenType.STAR, TokenType.SLASH)) {
                Token op = previous();
                BigDecimal right = parseFactor();
                value = apply(value, op.type(), right);
            }
            return value;
        }

        private BigDecimal parseFactor() {
            if (match(TokenType.MINUS)) {
                BigDecimal value = parseFactor();
                return value == null ? null : value.negate();
            }
            if (match(TokenType.PLUS)) {
                return parseFactor();
            }
            return parsePrimary();
        }

        private BigDecimal parsePrimary() {
            Token current = peek();
            if (current == null) {
                throw new IllegalArgumentException("公式表达式意外结束: " + source);
            }
            switch (current.type()) {
                case NUMBER -> {
                    advance();
                    return new BigDecimal(current.text());
                }
                case REF -> {
                    advance();
                    return numeric(row.get(current.text()));
                }
                case LEFT_PAREN -> {
                    advance();
                    BigDecimal value = parseExpression();
                    expect(TokenType.RIGHT_PAREN, "公式表达式缺少右括号: " + source);
                    return value;
                }
                case IDENT -> {
                    advance();
                    return callFunction(current.text());
                }
                default -> throw new IllegalArgumentException(
                        "公式表达式无法解析，位置 " + current.start() + ": " + source);
            }
        }

        private BigDecimal callFunction(String name) {
            if (!"ROUND".equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("不支持的公式函数: " + name);
            }
            expect(TokenType.LEFT_PAREN, "函数 ROUND 缺少左括号: " + source);
            BigDecimal argument = parseExpression();
            int scale = 0;
            if (match(TokenType.COMMA)) {
                BigDecimal scaleValue = parseExpression();
                if (scaleValue != null) {
                    scale = scaleValue.intValue();
                }
            }
            expect(TokenType.RIGHT_PAREN, "函数 ROUND 缺少右括号: " + source);
            if (argument == null) {
                return null;
            }
            return argument.setScale(scale, RoundingMode.HALF_UP);
        }

        private static BigDecimal apply(BigDecimal left, TokenType operator, BigDecimal right) {
            if (left == null || right == null) {
                return null;
            }
            return switch (operator) {
                case PLUS -> left.add(right);
                case MINUS -> left.subtract(right);
                case STAR -> left.multiply(right);
                case SLASH -> right.compareTo(BigDecimal.ZERO) == 0
                        ? null
                        : left.divide(right, DEFAULT_DECIMAL_SCALE, RoundingMode.HALF_UP);
                default -> throw new IllegalArgumentException("意外的运算符: " + operator);
            };
        }

        private static BigDecimal numeric(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            if (value instanceof Boolean bool) {
                return bool ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            if (value instanceof LocalDate) {
                // 日期不参与算术，按 null 处理，避免误导。
                return null;
            }
            try {
                return new BigDecimal(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private boolean match(TokenType... types) {
            Token current = peek();
            if (current == null) {
                return false;
            }
            for (TokenType type : types) {
                if (current.type() == type) {
                    cursor++;
                    return true;
                }
            }
            return false;
        }

        private Token peek() {
            return cursor < tokens.size() ? tokens.get(cursor) : null;
        }

        private Token previous() {
            return tokens.get(cursor - 1);
        }

        private void advance() {
            cursor++;
        }

        private void expect(TokenType type, String message) {
            Token current = peek();
            if (current == null || current.type() != type) {
                throw new IllegalArgumentException(message);
            }
            cursor++;
        }
    }

    private enum TokenType {
        NUMBER, REF, IDENT, LEFT_PAREN, RIGHT_PAREN, COMMA, PLUS, MINUS, STAR, SLASH
    }

    private record Token(TokenType type, String text, int start) {
    }

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int length = source.length();
        int index = 0;
        while (index < length) {
            char ch = source.charAt(index);
            if (Character.isWhitespace(ch)) {
                index++;
                continue;
            }
            if (ch == '{') {
                int end = source.indexOf('}', index);
                if (end < 0) {
                    throw new IllegalArgumentException("公式字段引用缺少 '}': " + source);
                }
                String code = source.substring(index + 1, end).trim();
                if (code.isEmpty()) {
                    throw new IllegalArgumentException("公式字段引用不能为空: " + source);
                }
                tokens.add(new Token(TokenType.REF, code, index));
                index = end + 1;
                continue;
            }
            if (Character.isDigit(ch) || ch == '.') {
                int start = index;
                boolean seenDot = false;
                while (index < length
                        && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) {
                    if (source.charAt(index) == '.') {
                        if (seenDot) {
                            throw new IllegalArgumentException("公式数字格式错误: " + source);
                        }
                        seenDot = true;
                    }
                    index++;
                }
                tokens.add(new Token(TokenType.NUMBER, source.substring(start, index), start));
                continue;
            }
            if (Character.isLetter(ch) || ch == '_') {
                int start = index;
                while (index < length
                        && (Character.isLetterOrDigit(source.charAt(index)) || source.charAt(index) == '_')) {
                    index++;
                }
                tokens.add(new Token(TokenType.IDENT, source.substring(start, index), start));
                continue;
            }
            switch (ch) {
                case '(' -> tokens.add(new Token(TokenType.LEFT_PAREN, "(", index));
                case ')' -> tokens.add(new Token(TokenType.RIGHT_PAREN, ")", index));
                case ',' -> tokens.add(new Token(TokenType.COMMA, ",", index));
                case '+' -> tokens.add(new Token(TokenType.PLUS, "+", index));
                case '-' -> tokens.add(new Token(TokenType.MINUS, "-", index));
                case '*' -> tokens.add(new Token(TokenType.STAR, "*", index));
                case '/' -> tokens.add(new Token(TokenType.SLASH, "/", index));
                default -> throw new IllegalArgumentException(
                        "公式表达式含非法字符 '" + ch + "': " + source);
            }
            index++;
        }
        return tokens;
    }
}
