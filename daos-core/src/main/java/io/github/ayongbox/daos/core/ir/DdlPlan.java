package io.github.ayongbox.daos.core.ir;

import java.util.List;
import java.util.Objects;

/**
 * 结构演进计划。语句已按目标方言生成，执行与回滚由 runtime 负责。
 */
public record DdlPlan(List<Statement> statements) {

    public enum Operation {
        CREATE_TABLE,
        DROP_TABLE,
        ADD_COLUMN,
        DROP_COLUMN,
        CREATE_INDEX,
        DROP_INDEX
    }

    public record Statement(Operation operation, String objectName, String sql) {
        public Statement {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(objectName, "objectName");
            Objects.requireNonNull(sql, "sql");
        }
    }

    public DdlPlan {
        statements = statements == null ? List.of() : List.copyOf(statements);
    }

    public static DdlPlan empty() {
        return new DdlPlan(List.of());
    }

    public boolean isEmpty() {
        return statements.isEmpty();
    }
}
