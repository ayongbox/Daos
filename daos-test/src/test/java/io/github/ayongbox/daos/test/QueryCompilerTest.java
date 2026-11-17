package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryPlan;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.engine.dialect.MysqlDialect;
import io.github.ayongbox.daos.runtime.engine.query.DefaultQueryCompiler;
import io.github.ayongbox.daos.runtime.engine.query.QueryCompileException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCompilerTest {

    private static final String APP_ID = "app-1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private final DefaultQueryCompiler compiler = new DefaultQueryCompiler();
    private final PermissionContext permissions =
            new PermissionContext("tenant-1", "user-1", List.of(), List.of(), List.of());

    private NormalizedSchema schema;

    @BeforeEach
    void buildSchema() {
        SchemaTable users = table("t1", "users", StorageMode.PHYSICAL, List.of(), List.of(
                text("t1", "f1", "title", true, true),
                text("t1", "f2", "memo", false, false),
                formula("t1", "f3", "total")));
        SchemaTable items = table("t2", "items", StorageMode.HYBRID, List.of("amount"), List.of(
                text("t2", "f4", "amount", true, true),
                text("t2", "f5", "remark", false, false)));

        schema = new SchemaNormalizer().normalize("demo", "snap-1", 1L, List.of(users, items));
    }

    @Test
    void compilesProjectionFilterSortAndPagination() {
        QueryRequest request = new QueryRequest(
                List.of("title"),
                new FilterNode.And(List.of(
                        new FilterNode.Compare("title", ConditionOperator.CONTAINS, List.of("道")),
                        new FilterNode.IsNull("title", true))),
                List.of(new QueryRequest.SortSpec("title", SortDirection.DESC)),
                2,
                10);

        QueryPlan.Select select = (QueryPlan.Select) compiler.compile(schema, "t1", request, permissions);

        assertEquals("app_demo_users", select.physicalTable());
        assertEquals("title", select.items().get(0).column());
        assertEquals("title", select.items().get(0).alias());
        FilterNode.And filter = assertInstanceOf(FilterNode.And.class, select.filter());
        assertEquals(2, filter.children().size());
        assertFalse(select.orderBy().get(0).ascending());
        assertEquals(10, select.limit());
        assertEquals(10, select.offset());
    }

    @Test
    void rewritesLogicalOperatorsIntoParameterizedSql() {
        QueryRequest request = new QueryRequest(
                List.of("title"),
                new FilterNode.Compare("title", ConditionOperator.EQ, List.of("道生")),
                List.of(new QueryRequest.SortSpec("title", SortDirection.ASC)),
                2,
                10);

        QueryPlan plan = compiler.compile(schema, "t1", request, permissions);
        SqlStatement sql = new MysqlDialect().query().render(plan);

        assertEquals("SELECT `title` FROM `app_demo_users` WHERE `title` = ? "
                + "ORDER BY `title` ASC LIMIT 10 OFFSET 10", sql.sql());
        assertEquals(List.of("道生"), sql.parameters());
    }

    @Test
    void rejectsUnknownProjectionField() {
        QueryRequest request = new QueryRequest(List.of("missing"), null, null, 1, 20);

        QueryCompileException error = assertThrows(QueryCompileException.class,
                () -> compiler.compile(schema, "t1", request, permissions));

        assertTrue(error.getMessage().contains("不存在字段"));
    }

    @Test
    void rejectsDerivedAndJsonMemberProjection() {
        QueryCompileException derived = assertThrows(QueryCompileException.class,
                () -> compiler.compile(schema, "t1",
                        new QueryRequest(List.of("total"), null, null, 1, 20), permissions));
        assertTrue(derived.getMessage().contains("派生字段"));

        QueryCompileException jsonMember = assertThrows(QueryCompileException.class,
                () -> compiler.compile(schema, "t2",
                        new QueryRequest(List.of("remark"), null, null, 1, 20), permissions));
        assertTrue(jsonMember.getMessage().contains("JSON 成员字段"));

        QueryPlan.Select promoted = (QueryPlan.Select) compiler.compile(schema, "t2",
                new QueryRequest(List.of("amount"), null, null, 1, 20), permissions);
        assertEquals("amount", promoted.items().get(0).column());
    }

    @Test
    void rejectsFilterOnNonFilterableField() {
        QueryRequest request = new QueryRequest(
                List.of("title"),
                new FilterNode.Compare("memo", ConditionOperator.EQ, List.of("x")),
                null, 1, 20);

        QueryCompileException error = assertThrows(QueryCompileException.class,
                () -> compiler.compile(schema, "t1", request, permissions));

        assertTrue(error.getMessage().contains("不可筛选"));
    }

    @Test
    void rejectsSortOnNonSortableField() {
        QueryRequest request = new QueryRequest(
                List.of("title"), null,
                List.of(new QueryRequest.SortSpec("memo", SortDirection.ASC)), 1, 20);

        QueryCompileException error = assertThrows(QueryCompileException.class,
                () -> compiler.compile(schema, "t1", request, permissions));

        assertTrue(error.getMessage().contains("不可排序"));
    }

    @Test
    void rejectsSemanticOperatorBeforeRendering() {
        QueryRequest request = new QueryRequest(
                List.of("title"),
                new FilterNode.Compare("title", ConditionOperator.ANY_OF, List.of("x")),
                null, 1, 20);

        QueryCompileException error = assertThrows(QueryCompileException.class,
                () -> compiler.compile(schema, "t1", request, permissions));

        assertTrue(error.getMessage().contains("权限重写阶段"));
    }

    @Test
    void compilesNotNodeAndRendersNegation() {
        QueryRequest request = new QueryRequest(
                List.of("title"),
                new FilterNode.Not(new FilterNode.Compare("title", ConditionOperator.EQ, List.of("x"))),
                null, 1, 20);

        QueryPlan plan = compiler.compile(schema, "t1", request, permissions);
        SqlStatement sql = new MysqlDialect().query().render(plan);

        assertEquals("SELECT `title` FROM `app_demo_users` WHERE NOT (`title` = ?) LIMIT 20", sql.sql());
        assertEquals(List.of("x"), sql.parameters());
    }

    private static SchemaTable table(
            String id,
            String code,
            StorageMode storageMode,
            List<String> promotedColumns,
            List<SchemaField> fields) {
        return new SchemaTable(id, APP_ID, TableOwnership.APPLICATION, false,
                storageMode, promotedColumns, null,
                code, code, null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields, List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField text(
            String tableId, String id, String code, boolean filterable, boolean sortable) {
        return new SchemaField(id, APP_ID, tableId, code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                new FieldConstraints(false, false, false, false, sortable, filterable,
                        false, false, true, null, null, false, null),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField formula(String tableId, String id, String code) {
        return new SchemaField(id, APP_ID, tableId, code, code, null,
                FieldLogicalType.FORMULA,
                new FieldConfig.FormulaConfig("1 + 1", "number", 0, "REALTIME"),
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
