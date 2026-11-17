package io.github.ayongbox.daos.core.metadata.permission;

import io.github.ayongbox.daos.core.metadata.common.LogicOperator;

import java.util.List;

/**
 * 行级条件树，最多 3 层。编译器的独立重写阶段把它转成 AST 条件。
 */
public record RowCondition(
        LogicOperator logic,
        List<ConditionItem> items,
        List<RowCondition> groups) {

    public static final int MAX_DEPTH = 3;

    public RowCondition {
        logic = logic == null ? LogicOperator.AND : logic;
        items = items == null ? List.of() : List.copyOf(items);
        groups = groups == null ? List.of() : List.copyOf(groups);
        if (items.isEmpty() && groups.isEmpty()) {
            throw new IllegalArgumentException("行级条件不能为空");
        }
    }

    public int depth() {
        int max = 0;
        for (RowCondition group : groups) {
            max = Math.max(max, group.depth());
        }
        return max + 1;
    }
}
