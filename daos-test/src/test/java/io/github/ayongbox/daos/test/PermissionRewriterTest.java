package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.DataScopeType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.LogicOperator;
import io.github.ayongbox.daos.core.metadata.common.MaskType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.SubjectType;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.common.ValueType;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.permission.ConditionItem;
import io.github.ayongbox.daos.core.metadata.permission.DataScope;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.MaskRule;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.RowCondition;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;
import io.github.ayongbox.daos.runtime.engine.query.DefaultQueryCompiler;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriteException;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionRewriterTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private final DefaultQueryCompiler compiler = new DefaultQueryCompiler();
    private final PermissionRewriter rewriter = new PermissionRewriter();

    private NormalizedSchema schema;

    @BeforeEach
    void buildSchema() {
        SchemaTable users = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(),
                List.of(
                        field("f_title", "title"),
                        field("f_owner", "owner"),
                        field("f_dept", "dept"),
                        field("f_secret", "secret")),
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        schema = new SchemaNormalizer().normalize(APP_CODE, "snap-1", 1L, List.of(users));
    }

    @Test
    void defaultsToEmptyResultWithoutAllowRule() {
        SqlStatement sql = rewrite(request("title"), context(List.of()));

        assertTrue(sql.sql().contains("1 = 0"));
    }

    @Test
    void injectsTenantAndSoftDeleteForAllowedRule() {
        SqlStatement sql = rewrite(request("title"), context(List.of(allowAll("p1"))));

        assertTrue(sql.sql().contains("\"tenant_id\" = ?"));
        assertTrue(sql.sql().contains("\"deleted\" = ?"));
        assertTrue(sql.parameters().contains("tenant-1"));
        assertTrue(sql.parameters().contains(Boolean.FALSE));
    }

    @Test
    void crossTenantSkipsTenantPredicate() {
        PermissionContext crossTenant = new PermissionContext(
                "", "root", List.of(), List.of(), List.of(allowAll("p1")));

        SqlStatement sql = rewrite(request("title"), crossTenant);

        assertFalse(sql.sql().contains("\"tenant_id\""));
        assertTrue(sql.sql().contains("\"deleted\" = ?"));
    }

    @Test
    void selfScopeCompilesToOwnerEquality() {
        DataScope scope = new DataScope(
                DataScopeType.SELF, "f_owner", null, List.of(), List.of(), List.of(), false, false);

        SqlStatement sql = rewrite(request("title"), context(List.of(allow("p1", scope, null, null))));

        assertTrue(sql.sql().contains("\"owner\" = ?"));
        assertTrue(sql.parameters().contains("user-1"));
    }

    @Test
    void deptScopeWithoutDepartmentsYieldsEmptyResult() {
        DataScope scope = new DataScope(
                DataScopeType.DEPT, null, "f_dept", List.of(), List.of(), List.of(), true, false);

        SqlStatement sql = rewrite(request("title"), context(List.of(allow("p1", scope, null, null))));

        assertTrue(sql.sql().contains("1 = 0"));
    }

    @Test
    void deptScopeUsesResolvedDepartmentIds() {
        DataScope scope = new DataScope(
                DataScopeType.DEPT_TREE, null, "f_dept", List.of(), List.of(), List.of(), true, false);
        PermissionContext permissions = new PermissionContext(
                "tenant-1", "user-1", List.of(), List.of("d1", "d2"),
                List.of(allow("p1", scope, null, null)));

        SqlStatement sql = rewrite(request("title"), permissions);

        assertTrue(sql.sql().contains("\"dept\" IN (?, ?)"));
        assertTrue(sql.parameters().containsAll(List.of("d1", "d2")));
    }

    @Test
    void customScopeCombinesUsersAndDepartments() {
        DataScope scope = new DataScope(
                DataScopeType.CUSTOM, "f_owner", "f_dept",
                List.of("u1", "u2"), List.of("d1"), List.of(), false, false);

        SqlStatement sql = rewrite(request("title"), context(List.of(allow("p1", scope, null, null))));

        assertTrue(sql.sql().contains("\"owner\" IN (?, ?)"));
        assertTrue(sql.sql().contains("\"dept\" IN (?)"));
        assertTrue(sql.sql().contains(" OR "));
    }

    @Test
    void resolvesSelfOperatorInRowCondition() {
        RowCondition condition = condition(new ConditionItem(
                "title", ConditionOperator.IS_SELF, ValueType.ANY, null, null, null));

        SqlStatement sql = rewrite(request("title"), context(List.of(allow("p1", null, condition, null))));

        assertTrue(sql.sql().contains("\"title\" = ?"));
        assertTrue(sql.parameters().contains("user-1"));
    }

    @Test
    void resolvesNestedConditionGroups() {
        RowCondition inner = new RowCondition(LogicOperator.OR, List.of(
                new ConditionItem("title", ConditionOperator.EQ, ValueType.STRING, "a", null, null),
                new ConditionItem("title", ConditionOperator.EQ, ValueType.STRING, "b", null, null)),
                List.of());
        RowCondition outer = new RowCondition(LogicOperator.AND, List.of(
                new ConditionItem("dept", ConditionOperator.ANY_OF, ValueType.ANY,
                        List.of("d1", "d2"), null, null)),
                List.of(inner));

        SqlStatement sql = rewrite(request("title"), context(List.of(allow("p1", null, outer, null))));

        assertTrue(sql.sql().contains("\"dept\" IN (?, ?)"));
        assertTrue(sql.sql().contains("\"title\" = ? OR \"title\" = ?"));
    }

    @Test
    void denyRuleBecomesNegatedPredicate() {
        DataScope denyScope = new DataScope(
                DataScopeType.SELF, "f_owner", null, List.of(), List.of(), List.of(), false, false);
        SchemaPermission deny = permission(
                "deny-1", EffectType.DENY, denyScope, null, null, null, null);

        SqlStatement sql = rewrite(request("title"), context(List.of(allowAll("p1"), deny)));

        assertTrue(sql.sql().contains("NOT ("));
        assertTrue(sql.sql().contains("\"owner\" = ?"));
    }

    @Test
    void unconditionalDenyIsRejected() {
        SchemaPermission deny = permission(
                "deny-1", EffectType.DENY, null, null, null, null, null);

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> rewrite(request("title"), context(List.of(allowAll("p1"), deny))));

        assertTrue(error.getMessage().contains("显式拒绝"));
    }

    @Test
    void hiddenProjectionIsRemoved() {
        FieldMask mask = new FieldMask(List.of("f_secret"), List.of(), List.of(), List.of());
        QueryRequest request = new QueryRequest(List.of("title", "secret"), null, null, 1, 20);

        QueryPlan.Select select = rewritePlan(request, context(List.of(allow("p1", null, null, mask))));

        assertEquals(1, select.items().size());
        assertEquals("title", select.items().get(0).column());
    }

    @Test
    void hiddenFilterIsRejected() {
        FieldMask mask = new FieldMask(List.of("f_secret"), List.of(), List.of(), List.of());
        QueryRequest request = new QueryRequest(
                List.of("title"),
                new FilterNode.Compare("secret", ConditionOperator.EQ, List.of("x")),
                null, 1, 20);

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> rewrite(request, context(List.of(allow("p1", null, null, mask)))));

        assertTrue(error.getMessage().contains("FIELD_HIDDEN"));
    }

    @Test
    void hiddenSortIsRejected() {
        FieldMask mask = new FieldMask(List.of("f_secret"), List.of(), List.of(), List.of());
        QueryRequest request = new QueryRequest(
                List.of("title"), null,
                List.of(new QueryRequest.SortSpec("secret", SortDirection.ASC)), 1, 20);

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> rewrite(request, context(List.of(allow("p1", null, null, mask)))));

        assertTrue(error.getMessage().contains("FIELD_HIDDEN"));
    }

    @Test
    void expiredAllowRuleIsIgnored() {
        SchemaPermission expired = permission(
                "p1", EffectType.ALLOW, null, null, null,
                "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z");

        SqlStatement sql = rewrite(request("title"), context(List.of(expired)));

        assertTrue(sql.sql().contains("1 = 0"));
    }

    @Test
    void sharedRecordsScopeIsRejected() {
        DataScope scope = new DataScope(
                DataScopeType.SELF, "f_owner", null, List.of(), List.of(), List.of(), false, true);

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> rewrite(request("title"), context(List.of(allow("p1", scope, null, null)))));

        assertTrue(error.getMessage().contains("withShared"));
    }

    @Test
    void customRoleScopeIsRejected() {
        DataScope scope = new DataScope(
                DataScopeType.CUSTOM, null, null, List.of(), List.of(), List.of("r1"), false, false);

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> rewrite(request("title"), context(List.of(allow("p1", scope, null, null)))));

        assertTrue(error.getMessage().contains("roleIds"));
    }

    @Test
    void allOfOperatorIsRejected() {
        RowCondition condition = condition(new ConditionItem(
                "title", ConditionOperator.ALL_OF, ValueType.ANY, List.of("a", "b"), null, null));

        PermissionRewriteException error = assertThrows(PermissionRewriteException.class,
                () -> rewrite(request("title"), context(List.of(allow("p1", null, condition, null)))));

        assertTrue(error.getMessage().contains("all_of"));
    }

    @Test
    void mergesFieldMasksAcrossAllowRules() {
        FieldMask first = new FieldMask(
                List.of("f_secret"), List.of("f_title"), List.of(),
                List.of(new MaskRule("f_owner", MaskType.PHONE, null, null)));
        FieldMask second = new FieldMask(
                List.of(), List.of(), List.of("f_dept"),
                List.of(new MaskRule("f_owner", MaskType.EMAIL, null, null)));

        FieldMask merged = rewriter.effectiveFieldMask(
                schema, schema.requireTable("t1"),
                context(List.of(allow("p1", null, null, first), allow("p2", null, null, second))),
                NOW);

        assertEquals(List.of("f_secret"), merged.hiddenFieldIds());
        assertEquals(List.of("f_title"), merged.readonlyFieldIds());
        assertEquals(List.of("f_dept"), merged.requiredFieldIds());
        assertEquals(1, merged.maskRules().size());
        assertEquals(MaskType.PHONE, merged.maskRules().get(0).type());
    }

    private SqlStatement rewrite(QueryRequest request, PermissionContext permissions) {
        return new PostgresDialect().query().render(rewritePlan(request, permissions));
    }

    private QueryPlan.Select rewritePlan(QueryRequest request, PermissionContext permissions) {
        QueryPlan.Select base = (QueryPlan.Select) compiler.compile(schema, "t1", request, permissions);
        return rewriter.rewrite(schema, schema.requireTable("t1"), base, request, permissions, NOW);
    }

    private static QueryRequest request(String fieldCode) {
        return new QueryRequest(List.of(fieldCode), null, null, 1, 20);
    }

    private static PermissionContext context(List<SchemaPermission> rules) {
        return new PermissionContext("tenant-1", "user-1", List.of("role-1"), List.of(), rules);
    }

    private static SchemaPermission allowAll(String code) {
        return permission(code, EffectType.ALLOW, null, null, null, null, null);
    }

    private static SchemaPermission allow(
            String code, DataScope scope, RowCondition condition, FieldMask mask) {
        return permission(code, EffectType.ALLOW, scope, condition, mask, null, null);
    }

    private static SchemaPermission permission(
            String code,
            EffectType effect,
            DataScope scope,
            RowCondition condition,
            FieldMask mask,
            String effectiveFrom,
            String effectiveTo) {
        return new SchemaPermission(
                code, APP_ID, code, code, null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, "users", List.of()),
                List.of(ActionType.READ), effect, condition, scope, mask,
                100, true, effectiveFrom, effectiveTo, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static RowCondition condition(ConditionItem item) {
        return new RowCondition(LogicOperator.AND, List.of(item), List.of());
    }

    private static SchemaField field(String id, String code) {
        return new SchemaField(
                id, APP_ID, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                new FieldConstraints(false, false, false, false, true, true,
                        false, false, true, null, null, false, null),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
