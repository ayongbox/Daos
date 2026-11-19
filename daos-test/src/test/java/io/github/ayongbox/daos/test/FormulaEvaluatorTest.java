package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.runtime.formula.FormulaEvaluator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormulaEvaluatorTest {

    private final FormulaEvaluator evaluator = new FormulaEvaluator();

    private FieldConfig.FormulaConfig formula(String expression, String resultType) {
        return new FieldConfig.FormulaConfig(expression, resultType, 2, "NONE");
    }

    @Test
    void evaluatesConstantArithmetic() {
        Object result = evaluator.evaluate(formula("1 + 2 * 3", "number"), Map.of());
        assertThat(result).isEqualTo(7L);
    }

    @Test
    void respectsParentheses() {
        Object result = evaluator.evaluate(formula("(1 + 2) * 3", "number"), Map.of());
        assertThat(result).isEqualTo(9L);
    }

    @Test
    void resolvesFieldReferences() {
        Object result = evaluator.evaluate(
                formula("{qty} * {price}", "decimal"),
                Map.of("qty", 3, "price", new BigDecimal("9.50")));
        assertThat(result).isEqualTo(new BigDecimal("28.50"));
    }

    @Test
    void unaryMinus() {
        Object result = evaluator.evaluate(formula("-{amount} + 10", "number"), Map.of("amount", 4));
        assertThat(result).isEqualTo(6L);
    }

    @Test
    void roundFunction() {
        // 字段 precision=2，最终存储刻度由字段决定，ROUND 只控制中间精度。
        Object result = evaluator.evaluate(formula("ROUND({value}, 1)", "decimal"), Map.of("value", "3.14159"));
        assertThat(result).isEqualTo(new BigDecimal("3.10"));
    }

    @Test
    void missingOperandReturnsNull() {
        Object result = evaluator.evaluate(formula("{a} + {b}", "number"), Map.of("a", 1));
        assertThat(result).isNull();
    }

    @Test
    void divisionByZeroReturnsNull() {
        Object result = evaluator.evaluate(formula("{a} / {b}", "number"), Map.of("a", 1, "b", 0));
        assertThat(result).isNull();
    }

    @Test
    void chainedDependenciesEvaluateInOrder() {
        // 模拟调用方按拓扑序把中间结果喂回 row：先算 line，再用 line 算 total
        BigDecimal line = (BigDecimal) evaluator.evaluate(
                formula("{qty} * {price}", "decimal"), Map.of("qty", 2, "price", new BigDecimal("10")));
        Object total = evaluator.evaluate(
                formula("{line} * 1.1", "decimal"), Map.of("line", line));
        assertThat(total).isEqualTo(new BigDecimal("22.00"));
    }

    @Test
    void invalidExpressionThrows() {
        assertThatThrownBy(() -> evaluator.evaluate(formula("{a +", "number"), Map.of("a", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(formula("FOO({a})", "number"), Map.of("a", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(formula("1 + 2 3", "number"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("多余内容");
    }

    @Test
    void convertsResultTypeToText() {
        Object result = evaluator.evaluate(formula("2 + 3", "text"), Map.of());
        assertThat(result).isEqualTo("5");
    }

    @Test
    void convertsResultTypeToBoolean() {
        Object result = evaluator.evaluate(formula("{flag}", "boolean"), Map.of("flag", 0));
        assertThat(result).isEqualTo(false);
    }

    @Test
    void blankExpressionReturnsNull() {
        assertThat(evaluator.evaluate(formula("  ", "number"), Map.of())).isNull();
        assertThat(evaluator.evaluate(formula(null, "number"), Map.of())).isNull();
    }
}
