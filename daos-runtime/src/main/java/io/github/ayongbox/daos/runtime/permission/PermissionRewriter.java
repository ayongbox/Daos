package io.github.ayongbox.daos.runtime.permission;

import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.DataScopeType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.LogicOperator;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.permission.ConditionItem;
import io.github.ayongbox.daos.core.metadata.permission.DataScope;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.RowCondition;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.runtime.permission.PermissionRuleMatcher.SubjectMatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 权限重写阶段。把权限规则编译为 {@link FilterNode} 并注入查询计划，顺序固定为：
 * 租户 → 软删 → 允许集 → 拒绝集取反 → 用户条件。
 *
 * <p>合并语义来自 {@code metadata-schema-v1} 5.8：
 * {@code effective = tenant AND allowSet AND NOT denySet AND userFilter}。
 * 没有命中任何 ALLOW 规则时结果为空集，默认拒绝。</p>
 *
 * <p>无法安全编译的模型一律抛 {@link PermissionRewriteException}，禁止静默降级。
 * {@code PermissionContext.deptIds} 视为身份层已展开的可见部门集合，
 * 因此 {@code DEPT_TREE} 与自定义部门的 {@code includeSubDept} 不需要在此处再查组织树。</p>
 */
public final class PermissionRewriter {

    private static final String TENANT_COLUMN = "tenant_id";
    private static final String DELETED_COLUMN = "deleted";

    private static final Set<ConditionOperator> SEMANTIC_UNSUPPORTED = Set.of(
            ConditionOperator.ALL_OF,
            ConditionOperator.HAS_FILE,
            ConditionOperator.NO_FILE);

    public QueryPlan.Select rewrite(
            NormalizedSchema schema,
            IrTable table,
            QueryPlan.Select plan,
            QueryRequest request,
            PermissionContext permissions,
            Instant now) {

        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(now, "now");

        List<SchemaPermission> allows = new ArrayList<>();
        List<SchemaPermission> denies = new ArrayList<>();
        collect(schema, table, permissions, ActionType.READ, now, allows, denies);

        Set<String> hiddenColumns = hiddenColumns(table, allows);
        rejectHiddenFilter(request, plan, table, hiddenColumns);

        List<QueryPlan.SelectItem> items = plan.items().stream()
                .filter(item -> !hiddenColumns.contains(item.column()))
                .toList();

        List<FilterNode> predicates = new ArrayList<>();
        predicates.add(tenantPredicate(table, permissions));
        predicates.add(softDeletePredicate(table));
        predicates.add(allowPredicate(table, permissions, allows));
        if (!denies.isEmpty()) {
            predicates.add(new FilterNode.Not(orOf(denyPredicates(table, permissions, denies))));
        }
        predicates.add(plan.filter());

        return new QueryPlan.Select(
                plan.physicalTable(), items, conjoin(predicates), plan.orderBy(), plan.limit(), plan.offset());
    }

    /**
     * 汇总当前主体在目标表上的字段掩码。多条 ALLOW 规则取并集，脱敏规则按优先级取第一条。
     */
    public FieldMask effectiveFieldMask(
            NormalizedSchema schema,
            IrTable table,
            PermissionContext permissions,
            Instant now) {
        return effectiveFieldMask(schema, table, permissions, ActionType.READ, now);
    }

    /**
     * 汇总当前主体在指定动作上的字段掩码。没有命中 ALLOW 规则时返回空掩码。
     */
    public FieldMask effectiveFieldMask(
            NormalizedSchema schema,
            IrTable table,
            PermissionContext permissions,
            ActionType action,
            Instant now) {

        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(now, "now");

        List<SchemaPermission> allows = new ArrayList<>();
        List<SchemaPermission> denies = new ArrayList<>();
        collect(schema, table, permissions, action, now, allows, denies);
        return PermissionRuleMatcher.mergeFieldMask(allows);
    }

    /**
     * 解析写入动作的行级范围。没有命中 ALLOW 规则时直接拒绝，返回的条件供
     * UPDATE / DELETE 的 WHERE 复用，保证越权记录不会被命中。
     */
    public WritePermission resolveWrite(
            NormalizedSchema schema,
            IrTable table,
            PermissionContext permissions,
            ActionType action,
            Instant now) {

        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(now, "now");

        List<SchemaPermission> allows = new ArrayList<>();
        List<SchemaPermission> denies = new ArrayList<>();
        collect(schema, table, permissions, action, now, allows, denies);
        if (allows.isEmpty()) {
            throw new PermissionRewriteException(
                    "当前主体没有表 " + table.tableCode() + " 的 " + action.code() + " 权限");
        }

        List<FilterNode> predicates = new ArrayList<>();
        predicates.add(tenantPredicate(table, permissions));
        predicates.add(softDeletePredicate(table));
        predicates.add(allowPredicate(table, permissions, allows));
        if (!denies.isEmpty()) {
            predicates.add(new FilterNode.Not(orOf(denyPredicates(table, permissions, denies))));
        }
        return new WritePermission(conjoin(predicates), PermissionRuleMatcher.mergeFieldMask(allows));
    }

    /** 写入动作的解析结果：行级范围与字段掩码。 */
    public record WritePermission(FilterNode predicate, FieldMask fieldMask) {

        public WritePermission {
            Objects.requireNonNull(predicate, "predicate");
            Objects.requireNonNull(fieldMask, "fieldMask");
        }
    }

    private void collect(
            NormalizedSchema schema,
            IrTable table,
            PermissionContext permissions,
            ActionType action,
            Instant now,
            List<SchemaPermission> allows,
            List<SchemaPermission> denies) {

        for (SchemaPermission rule : permissions.permissions()) {
            if (!rule.enabled() || rule.deleted() || !PermissionRuleMatcher.active(rule, now)) {
                continue;
            }
            if (!matchesTable(rule, schema.appCode(), table.tableCode())
                    || !rule.actions().contains(action)) {
                continue;
            }

            SubjectMatch match = PermissionRuleMatcher.matchSubject(rule.subject(), permissions);
            if (match == SubjectMatch.UNKNOWN) {
                if (rule.effect() == EffectType.DENY) {
                    throw new PermissionRewriteException(
                            "权限 " + rule.code() + " 的主体缺少组织上下文，无法安全忽略显式拒绝");
                }
                continue;
            }
            if (match == SubjectMatch.NO_MATCH) {
                continue;
            }

            if (rule.effect() == EffectType.DENY) {
                if (isUnconditional(rule)) {
                    throw new PermissionRewriteException(
                            "权限 " + rule.code() + " 对当前主体显式拒绝表 " + table.tableCode() + " 的读取");
                }
                denies.add(rule);
            } else {
                allows.add(rule);
            }
        }
    }

    private boolean isUnconditional(SchemaPermission rule) {
        return rule.condition() == null
                && (rule.dataScope() == null || rule.dataScope().type() == DataScopeType.ALL);
    }

    /**
     * 表资源匹配：{@code objectCode} 为空表示应用内全部表，和应用维度的权限裁决保持一致。
     */
    private static boolean matchesTable(SchemaPermission rule, String appCode, String tableCode) {
        if (rule.resource().type() != ResourceType.TABLE
                || !appCode.equals(rule.resource().appCode())) {
            return false;
        }
        String objectCode = rule.resource().objectCode();
        return objectCode == null || objectCode.isBlank() || tableCode.equals(objectCode);
    }

    private FilterNode tenantPredicate(IrTable table, PermissionContext permissions) {
        if (!table.source().options().tenantScoped() || permissions.isCrossTenant()) {
            return FilterNode.alwaysTrue();
        }
        return new FilterNode.Compare(
                TENANT_COLUMN, ConditionOperator.EQ, List.of(permissions.tenantId()));
    }

    private FilterNode softDeletePredicate(IrTable table) {
        if (!table.source().options().softDelete()) {
            return FilterNode.alwaysTrue();
        }
        return new FilterNode.Compare(DELETED_COLUMN, ConditionOperator.EQ, List.of(Boolean.FALSE));
    }

    private FilterNode allowPredicate(
            IrTable table, PermissionContext permissions, List<SchemaPermission> allows) {
        if (allows.isEmpty()) {
            return FilterNode.alwaysFalse();
        }
        List<FilterNode> children = new ArrayList<>(allows.size());
        for (SchemaPermission rule : allows) {
            children.add(rulePredicate(table, permissions, rule));
        }
        return orOf(children);
    }

    private List<FilterNode> denyPredicates(
            IrTable table, PermissionContext permissions, List<SchemaPermission> denies) {
        List<FilterNode> children = new ArrayList<>(denies.size());
        for (SchemaPermission rule : denies) {
            children.add(rulePredicate(table, permissions, rule));
        }
        return children;
    }

    private FilterNode rulePredicate(
            IrTable table, PermissionContext permissions, SchemaPermission rule) {
        FilterNode scope = rowScope(table, rule.dataScope(), permissions);
        FilterNode condition = rule.condition() == null
                ? null : compileCondition(table, rule.condition(), permissions);
        List<FilterNode> children = new ArrayList<>(2);
        children.add(scope);
        children.add(condition);
        return conjoin(children);
    }

    private FilterNode rowScope(IrTable table, DataScope scope, PermissionContext permissions) {
        if (scope == null) {
            return FilterNode.alwaysTrue();
        }
        if (scope.withShared()) {
            throw new PermissionRewriteException(
                    "数据范围 withShared=true 需要共享记录数据源，当前运行时未提供");
        }

        return switch (scope.type()) {
            case ALL -> FilterNode.alwaysTrue();
            case SELF -> new FilterNode.Compare(
                    requireScopeColumn(table, scope.ownerFieldId(), "SELF.ownerFieldId").physicalName(),
                    ConditionOperator.EQ,
                    List.of(permissions.subjectId()));
            case DEPT, DEPT_TREE -> departmentPredicate(
                    requireScopeColumn(table, scope.deptFieldId(), scope.type() + ".deptFieldId"),
                    permissions.deptIds());
            case CUSTOM -> customScope(table, scope, permissions);
        };
    }

    private FilterNode customScope(IrTable table, DataScope scope, PermissionContext permissions) {
        if (!scope.roleIds().isEmpty()) {
            throw new PermissionRewriteException(
                    "CUSTOM 数据范围的 roleIds 需要身份层解析为用户集合，运行时不能直接编译");
        }

        List<FilterNode> children = new ArrayList<>();
        if (!scope.userIds().isEmpty()) {
            children.add(new FilterNode.Compare(
                    requireScopeColumn(table, scope.ownerFieldId(), "CUSTOM.ownerFieldId").physicalName(),
                    ConditionOperator.IN,
                    new ArrayList<>(scope.userIds())));
        }
        if (!scope.deptIds().isEmpty()) {
            children.add(departmentPredicate(
                    requireScopeColumn(table, scope.deptFieldId(), "CUSTOM.deptFieldId"),
                    scope.deptIds()));
        }
        return orOf(children);
    }

    private IrColumn requireScopeColumn(IrTable table, String fieldId, String usage) {
        if (fieldId == null || fieldId.isBlank()) {
            throw new PermissionRewriteException("数据范围 " + usage + " 未配置字段");
        }
        IrColumn column = table.columnByFieldId(fieldId);
        if (column == null) {
            throw new PermissionRewriteException(
                    "数据范围 " + usage + " 引用的字段不存在: " + fieldId);
        }
        if (!column.hasPhysicalColumn()) {
            throw new PermissionRewriteException(
                    "数据范围 " + usage + " 引用的字段没有物理列: " + column.fieldCode());
        }
        return column;
    }

    private FilterNode compileCondition(
            IrTable table, RowCondition condition, PermissionContext permissions) {
        if (condition.depth() > RowCondition.MAX_DEPTH) {
            throw new PermissionRewriteException(
                    "行级条件嵌套超过 " + RowCondition.MAX_DEPTH + " 层，实际 " + condition.depth());
        }

        List<FilterNode> children = new ArrayList<>();
        for (ConditionItem item : condition.items()) {
            children.add(compileItem(table, item, permissions));
        }
        for (RowCondition group : condition.groups()) {
            children.add(compileCondition(table, group, permissions));
        }
        return condition.logic() == LogicOperator.AND ? conjoin(children) : orOf(children);
    }

    private FilterNode compileItem(
            IrTable table, ConditionItem item, PermissionContext permissions) {
        if (item.valueFieldCode() != null && !item.valueFieldCode().isBlank()) {
            throw new PermissionRewriteException(
                    "行级条件的 valueFieldCode 暂不支持字段间比较: " + item.valueFieldCode());
        }
        if (item.valueExpression() != null && !item.valueExpression().isBlank()) {
            throw new PermissionRewriteException(
                    "行级条件的 valueExpression 暂不支持表达式求值: " + item.valueExpression());
        }

        IrColumn column = table.columnsByCode().get(item.fieldCode());
        if (column == null) {
            throw new PermissionRewriteException(
                    "行级条件引用的字段不存在: " + table.tableCode() + "." + item.fieldCode());
        }
        if (!column.hasPhysicalColumn()) {
            throw new PermissionRewriteException(
                    "行级条件引用的字段没有物理列: " + table.tableCode() + "." + item.fieldCode());
        }

        String physical = column.physicalName();
        ConditionOperator operator = item.operator();
        if (SEMANTIC_UNSUPPORTED.contains(operator)) {
            throw new PermissionRewriteException(
                    "行级条件运算符 " + operator.code() + " 无法编译为物理列条件");
        }

        return switch (operator) {
            case IS_SELF, CURRENT_USER -> new FilterNode.Compare(
                    physical, ConditionOperator.EQ, List.of(permissions.subjectId()));
            case IN_DEPT, CURRENT_DEPT, IN_DEPT_TREE, CURRENT_DEPT_TREE ->
                    departmentPredicate(column, permissions.deptIds());
            case CURRENT_TENANT -> permissions.isCrossTenant()
                    ? FilterNode.alwaysTrue()
                    : new FilterNode.Compare(
                            physical, ConditionOperator.EQ, List.of(permissions.tenantId()));
            case EMPTY -> new FilterNode.IsNull(physical, false);
            case NOT_EMPTY -> new FilterNode.IsNull(physical, true);
            case ANY_OF -> in(physical, valuesOf(item));
            case NONE_OF -> notIn(physical, valuesOf(item));
            case IN -> in(physical, valuesOf(item));
            case NOT_IN -> notIn(physical, valuesOf(item));
            case BETWEEN -> between(physical, valuesOf(item));
            default -> single(physical, operator, valuesOf(item));
        };
    }

    private FilterNode departmentPredicate(IrColumn column, List<String> deptIds) {
        if (deptIds.isEmpty()) {
            return FilterNode.alwaysFalse();
        }
        return new FilterNode.Compare(
                column.physicalName(), ConditionOperator.IN, new ArrayList<>(deptIds));
    }

    private FilterNode in(String column, List<Object> values) {
        if (values.isEmpty()) {
            return FilterNode.alwaysFalse();
        }
        return new FilterNode.Compare(column, ConditionOperator.IN, values);
    }

    private FilterNode notIn(String column, List<Object> values) {
        if (values.isEmpty()) {
            return FilterNode.alwaysTrue();
        }
        return new FilterNode.Compare(column, ConditionOperator.NOT_IN, values);
    }

    private FilterNode between(String column, List<Object> values) {
        if (values.size() != 2 || values.contains(null)) {
            throw new PermissionRewriteException(
                    "BETWEEN 需要 2 个非空值，实际 " + values.size());
        }
        return new FilterNode.Compare(column, ConditionOperator.BETWEEN, values);
    }

    private FilterNode single(String column, ConditionOperator operator, List<Object> values) {
        if (values.size() != 1) {
            throw new PermissionRewriteException(
                    "运算符 " + operator.code() + " 需要 1 个值，实际 " + values.size());
        }
        if (values.get(0) == null
                && operator != ConditionOperator.EQ && operator != ConditionOperator.NE) {
            throw new PermissionRewriteException(
                    "运算符 " + operator.code() + " 不接受 null 值");
        }
        return new FilterNode.Compare(column, operator, values);
    }

    private List<Object> valuesOf(ConditionItem item) {
        Object value = item.value();
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of(value);
    }

    private Set<String> hiddenColumns(IrTable table, List<SchemaPermission> allows) {
        Set<String> hiddenFieldIds = new HashSet<>();
        for (SchemaPermission rule : allows) {
            hiddenFieldIds.addAll(rule.fieldMask().hiddenFieldIds());
        }

        Set<String> columns = new HashSet<>();
        for (String fieldId : hiddenFieldIds) {
            IrColumn column = table.columnByFieldId(fieldId);
            if (column != null) {
                columns.add(column.physicalName());
            }
        }
        return columns;
    }

    private void rejectHiddenFilter(
            QueryRequest request, QueryPlan.Select plan, IrTable table, Set<String> hiddenColumns) {
        if (hiddenColumns.isEmpty()) {
            return;
        }
        for (QueryRequest.SortSpec sort : request.sorts()) {
            IrColumn column = table.columnsByCode().get(sort.fieldCode());
            if (column != null && hiddenColumns.contains(column.physicalName())) {
                throw new PermissionRewriteException(
                        "排序字段 " + sort.fieldCode() + " 对当前主体隐藏 (FIELD_HIDDEN)");
            }
        }
        rejectHiddenNode(plan.filter(), hiddenColumns);
    }

    private void rejectHiddenNode(FilterNode node, Set<String> hiddenColumns) {
        if (node instanceof FilterNode.And and) {
            and.children().forEach(child -> rejectHiddenNode(child, hiddenColumns));
        } else if (node instanceof FilterNode.Or or) {
            or.children().forEach(child -> rejectHiddenNode(child, hiddenColumns));
        } else if (node instanceof FilterNode.Not not) {
            rejectHiddenNode(not.child(), hiddenColumns);
        } else if (node instanceof FilterNode.IsNull isNull) {
            rejectHiddenColumn(isNull.column(), hiddenColumns);
        } else if (node instanceof FilterNode.Compare compare) {
            rejectHiddenColumn(compare.column(), hiddenColumns);
        }
    }

    private void rejectHiddenColumn(String column, Set<String> hiddenColumns) {
        if (hiddenColumns.contains(column)) {
            throw new PermissionRewriteException(
                    "筛选字段 " + column + " 对当前主体隐藏 (FIELD_HIDDEN)");
        }
    }

    private FilterNode conjoin(List<FilterNode> nodes) {
        List<FilterNode> children = new ArrayList<>();
        for (FilterNode node : nodes) {
            if (node == null || isAlwaysTrue(node)) {
                continue;
            }
            children.add(node);
        }
        if (children.isEmpty()) {
            return FilterNode.alwaysTrue();
        }
        if (children.size() == 1) {
            return children.get(0);
        }
        return new FilterNode.And(children);
    }

    private FilterNode orOf(List<FilterNode> nodes) {
        List<FilterNode> children = new ArrayList<>();
        for (FilterNode node : nodes) {
            if (node == null || isAlwaysFalse(node)) {
                continue;
            }
            if (isAlwaysTrue(node)) {
                return FilterNode.alwaysTrue();
            }
            children.add(node);
        }
        if (children.isEmpty()) {
            return FilterNode.alwaysFalse();
        }
        if (children.size() == 1) {
            return children.get(0);
        }
        return new FilterNode.Or(children);
    }

    private boolean isAlwaysTrue(FilterNode node) {
        return node instanceof FilterNode.Constant constant && constant.value();
    }

    private boolean isAlwaysFalse(FilterNode node) {
        return node instanceof FilterNode.Constant constant && !constant.value();
    }
}
