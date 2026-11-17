package io.github.ayongbox.daos.core.ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 结构演进计划。语句已按目标方言生成，执行与回滚由 runtime 负责。
 *
 * <p>计划只描述差异：新增表、列与索引的增删改，不包含数据搬迁。
 * 执行前必须落库到迁移台账，回滚依赖台账记录的前后快照。</p>
 */
public record MigrationPlan(
        String appId,
        String fromSnapshotId,
        long fromVersion,
        String toSnapshotId,
        long toVersion,
        List<MigrationStep> steps) {

    public enum Operation {
        CREATE_TABLE,
        DROP_TABLE,
        ADD_COLUMN,
        DROP_COLUMN,
        ALTER_COLUMN,
        RENAME_COLUMN,
        CREATE_INDEX,
        DROP_INDEX
    }

    public MigrationPlan {
        Objects.requireNonNull(appId, "appId");
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static MigrationPlan empty(String appId, String fromSnapshotId, long fromVersion,
                                      String toSnapshotId, long toVersion) {
        return new MigrationPlan(appId, fromSnapshotId, fromVersion, toSnapshotId, toVersion, List.of());
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public List<SqlStatement> forwardStatements() {
        return statements(steps, false);
    }

    /**
     * 回滚语句：按执行顺序的逆序执行每条步骤的回滚 SQL。
     */
    public List<SqlStatement> rollbackStatements() {
        List<MigrationStep> reversed = new ArrayList<>(steps);
        Collections.reverse(reversed);
        return statements(reversed, true);
    }

    private static List<SqlStatement> statements(List<MigrationStep> source, boolean rollback) {
        List<SqlStatement> statements = new ArrayList<>(source.size());
        for (MigrationStep step : source) {
            statements.add(SqlStatement.of(rollback ? step.rollbackSql() : step.forwardSql()));
        }
        return statements;
    }

    /**
     * 单条演进步骤。{@code objectName} 用于报错与审计定位，列步骤填物理列名或索引名。
     */
    public record MigrationStep(
            Operation operation,
            String tableCode,
            String objectName,
            String forwardSql,
            String rollbackSql) {

        public MigrationStep {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(tableCode, "tableCode");
            Objects.requireNonNull(objectName, "objectName");
            Objects.requireNonNull(forwardSql, "forwardSql");
            Objects.requireNonNull(rollbackSql, "rollbackSql");
        }

        public MigrationStep inverse() {
            return new MigrationStep(operation, tableCode, objectName, rollbackSql, forwardSql);
        }
    }
}
