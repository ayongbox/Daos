package io.github.ayongbox.daos.core.ir;

import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 过滤条件 AST。行级权限在独立重写阶段以 AND 方式注入，不与用户条件混合。
 */
public sealed interface FilterNode {

    record And(List<FilterNode> children) implements FilterNode {
        public And {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    record Or(List<FilterNode> children) implements FilterNode {
        public Or {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    record Compare(String column, ConditionOperator operator, List<Object> values) implements FilterNode {
        public Compare {
            Objects.requireNonNull(column, "column");
            Objects.requireNonNull(operator, "operator");
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    record IsNull(String column, boolean negated) implements FilterNode {
        public IsNull {
            Objects.requireNonNull(column, "column");
        }
    }

    record Not(FilterNode child) implements FilterNode {
        public Not {
            Objects.requireNonNull(child, "child");
        }
    }

    record Constant(boolean value) implements FilterNode {
    }

    static FilterNode alwaysTrue() {
        return new Constant(true);
    }

    static FilterNode alwaysFalse() {
        return new Constant(false);
    }

    static FilterNode and(FilterNode... nodes) {
        return new And(flatten(nodes));
    }

    static FilterNode or(FilterNode... nodes) {
        return new Or(flatten(nodes));
    }

    private static List<FilterNode> flatten(FilterNode[] nodes) {
        List<FilterNode> result = new ArrayList<>();
        Arrays.stream(nodes).filter(Objects::nonNull).forEach(result::add);
        if (result.size() == 1) {
            return List.of(result.get(0));
        }
        return List.copyOf(result);
    }
}
