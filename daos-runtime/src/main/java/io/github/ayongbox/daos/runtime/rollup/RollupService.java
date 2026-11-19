package io.github.ayongbox.daos.runtime.rollup;

import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.pipeline.dialect.SqlDialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataWriteService;
import io.github.ayongbox.daos.runtime.exec.WriteConflictException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 子表写入后的主表汇总。聚合查询与主表权限更新始终复用当前事务连接。 */
public final class RollupService {

    private static final String ID_COLUMN = "id";
    private static final String TENANT_COLUMN = "tenant_id";
    private static final String VERSION_COLUMN = "version";
    private static final String DELETED_COLUMN = "deleted";

    private final DataAccessFacade dataAccess;
    private final SqlDialect dialect;

    public RollupService(DataAccessFacade dataAccess, SqlDialect dialect) {
        this.dataAccess = Objects.requireNonNull(dataAccess, "dataAccess");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    /** 新主记录还没有子行，直接物化空集结果，同时覆盖调用方提交的伪造值。 */
    public void initializeValues(
            NormalizedSchema schema, IrTable table, Map<String, Object> business) {
        for (IrColumn column : table.columns()) {
            if (!(column.source().config() instanceof FieldConfig.RollupConfig rollup)
                    || column.source().logicalType() != FieldLogicalType.ROLLUP) {
                continue;
            }
            IrColumn source = sourceColumn(schema, table, rollup);
            business.put(column.physicalName(), emptyValue(rollup, source));
        }
    }

    /** 普通主表更新不能伪造汇总值；真正的汇总更新走 updateFromRollup。 */
    public void preserveStoredValues(
            IrTable table,
            Map<String, Object> supplied,
            Map<String, Object> business,
            Map<String, Object> currentRow) {
        for (IrColumn column : table.columns()) {
            if (column.source().logicalType() != FieldLogicalType.ROLLUP
                    || !supplied.containsKey(column.fieldCode())) {
                continue;
            }
            business.put(column.physicalName(), currentRow.get(column.fieldCode()));
        }
    }

    public boolean hasRelations(NormalizedSchema schema, IrTable childTable) {
        return !targets(schema, childTable).isEmpty();
    }

    /** before 为 null 表示新增，after 为 null 表示删除；关联换父时新旧主记录都重算。 */
    public void onWrite(
            NormalizedSchema schema,
            IrTable childTable,
            Map<String, Object> before,
            Map<String, Object> after,
            PermissionContext permissions,
            DataWriteService writeService) {

        for (Target target : targets(schema, childTable)) {
            Set<String> parentIds = new LinkedHashSet<>();
            addParentId(parentIds, before, target.relation().fieldCode());
            addParentId(parentIds, after, target.relation().fieldCode());
            for (String parentId : parentIds) {
                recalculate(target, parentId, permissions, writeService);
            }
        }
    }

    private void recalculate(
            Target target,
            String parentRecordId,
            PermissionContext permissions,
            DataWriteService writeService) {

        Map<String, Object> values = new LinkedHashMap<>();
        for (IrColumn rollupColumn : target.rollups()) {
            FieldConfig.RollupConfig rollup =
                    (FieldConfig.RollupConfig) rollupColumn.source().config();
            values.put(rollupColumn.fieldCode(), aggregate(target, rollup, parentRecordId, permissions));
        }
        if (values.isEmpty()) {
            return;
        }
        long version = currentVersion(target.parent(), parentRecordId, permissions);
        writeService.updateFromRollup(
                target.parent().source().appId(), target.parent().tableId(), parentRecordId,
                version, values, permissions);
    }

    private Object aggregate(
            Target target,
            FieldConfig.RollupConfig rollup,
            String parentRecordId,
            PermissionContext permissions) {

        String aggregate = aggregateName(rollup);
        if (rollup.filterExpression() != null && !rollup.filterExpression().isBlank()) {
            throw new IllegalArgumentException("汇总字段的过滤表达式暂未支持");
        }
        IrColumn source = "count".equals(aggregate)
                ? null : requireSource(target.child(), rollup.sourceFieldId());
        String expression = source == null ? "*" : dialect.quote(source.physicalName());
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(aggregate.toUpperCase(Locale.ROOT)).append('(').append(expression).append(')')
                .append(" AS ").append(dialect.quote("value"))
                .append(" FROM ").append(dialect.quote(target.child().physicalName()))
                .append(" WHERE ").append(dialect.quote(target.relation().physicalName()))
                .append(" = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(parentRecordId);
        appendLiveScope(target.child(), sql, parameters, permissions);

        List<Map<String, Object>> rows = dataAccess.query(new SqlStatement(sql.toString(), parameters));
        Object raw = rows.isEmpty() ? null : rows.get(0).get("value");
        if (raw == null) {
            return emptyValue(rollup, source);
        }
        if ("count".equals(aggregate)) {
            return ((Number) raw).longValue();
        }
        if ("sum".equals(aggregate) || "avg".equals(aggregate)) {
            return decimal(raw).setScale(scale(rollup), RoundingMode.HALF_UP);
        }
        return raw;
    }

    private long currentVersion(
            IrTable parent, String parentRecordId, PermissionContext permissions) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(dialect.quote(VERSION_COLUMN))
                .append(" FROM ").append(dialect.quote(parent.physicalName()))
                .append(" WHERE ").append(dialect.quote(ID_COLUMN)).append(" = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(parentRecordId);
        appendLiveScope(parent, sql, parameters, permissions);
        List<Map<String, Object>> rows = dataAccess.query(new SqlStatement(sql.toString(), parameters));
        if (rows.isEmpty()) {
            throw new WriteConflictException("汇总主记录不存在或已删除: " + parentRecordId);
        }
        Object version = rows.get(0).get(VERSION_COLUMN);
        if (!(version instanceof Number number)) {
            throw new WriteConflictException("汇总主记录版本无效: " + parentRecordId);
        }
        return number.longValue();
    }

    private void appendLiveScope(
            IrTable table, StringBuilder sql, List<Object> parameters, PermissionContext permissions) {
        if (table.source().options().softDelete()) {
            sql.append(" AND ").append(dialect.quote(DELETED_COLUMN)).append(" = ?");
            parameters.add(Boolean.FALSE);
        }
        if (table.source().options().tenantScoped()) {
            sql.append(" AND ").append(dialect.quote(TENANT_COLUMN)).append(" = ?");
            parameters.add(permissions.tenantId());
        }
    }

    private List<Target> targets(NormalizedSchema schema, IrTable childTable) {
        List<Target> result = new ArrayList<>();
        for (IrColumn relation : childTable.columns()) {
            if (!(relation.source().config() instanceof FieldConfig.RelationConfig relationConfig)
                    || relation.source().logicalType() != FieldLogicalType.RELATION) {
                continue;
            }
            IrTable parent = schema.tablesById().get(relationConfig.targetTableId());
            if (parent == null) {
                continue;
            }
            List<IrColumn> rollups = parent.columns().stream()
                    .filter(column -> column.source().logicalType() == FieldLogicalType.ROLLUP)
                    .filter(column -> column.source().config() instanceof FieldConfig.RollupConfig rollup
                            && relation.fieldId().equals(rollup.relationFieldId()))
                    .toList();
            if (rollups.isEmpty()) {
                continue;
            }
            if (relationConfig.multiple() || !relation.hasPhysicalColumn()) {
                throw new IllegalArgumentException(
                        "汇总关联字段必须是可物理写入的单选关联: " + relation.fieldCode());
            }
            result.add(new Target(parent, childTable, relation, rollups));
        }
        return result;
    }

    private IrColumn sourceColumn(
            NormalizedSchema schema, IrTable parent, FieldConfig.RollupConfig rollup) {
        if (rollup.relationFieldId() == null) {
            return null;
        }
        for (IrTable child : schema.tablesById().values()) {
            IrColumn relation = child.columnByFieldId(rollup.relationFieldId());
            if (relation == null
                    || !(relation.source().config() instanceof FieldConfig.RelationConfig config)
                    || !parent.tableId().equals(config.targetTableId())) {
                continue;
            }
            return "count".equals(aggregateName(rollup))
                    ? null : child.columnByFieldId(rollup.sourceFieldId());
        }
        return null;
    }

    private IrColumn requireSource(IrTable child, String sourceFieldId) {
        IrColumn source = sourceFieldId == null ? null : child.columnByFieldId(sourceFieldId);
        if (source == null || !source.hasPhysicalColumn()) {
            throw new IllegalArgumentException("汇总来源字段不存在或不可物理读取: " + sourceFieldId);
        }
        return source;
    }

    private Object emptyValue(FieldConfig.RollupConfig rollup, IrColumn source) {
        String aggregate = aggregateName(rollup);
        if ("count".equals(aggregate)) {
            return 0L;
        }
        if (!rollup.emptyAsZero()) {
            return null;
        }
        if ("sum".equals(aggregate) || "avg".equals(aggregate)) {
            return BigDecimal.ZERO.setScale(scale(rollup), RoundingMode.HALF_UP);
        }
        if (source != null && source.source().logicalType() == FieldLogicalType.NUMBER) {
            return 0L;
        }
        if (source != null && source.source().logicalType() == FieldLogicalType.DECIMAL) {
            return BigDecimal.ZERO.setScale(scale(rollup), RoundingMode.HALF_UP);
        }
        return null;
    }

    private String aggregateName(FieldConfig.RollupConfig rollup) {
        String aggregate = rollup.aggregate() == null
                ? "count" : rollup.aggregate().toLowerCase(Locale.ROOT);
        if (!Set.of("sum", "avg", "count", "max", "min").contains(aggregate)) {
            throw new IllegalArgumentException("不支持的汇总方式: " + rollup.aggregate());
        }
        return aggregate;
    }

    private int scale(FieldConfig.RollupConfig rollup) {
        return Math.max(0, Math.min(rollup.precision(), 20));
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal
                ? decimal : new BigDecimal(String.valueOf(value));
    }

    private void addParentId(Set<String> parentIds, Map<String, Object> row, String relationCode) {
        if (row == null) {
            return;
        }
        Object value = row.get(relationCode);
        if (value != null && !String.valueOf(value).isBlank()) {
            parentIds.add(String.valueOf(value));
        }
    }

    private record Target(
            IrTable parent,
            IrTable child,
            IrColumn relation,
            List<IrColumn> rollups) {
    }
}
