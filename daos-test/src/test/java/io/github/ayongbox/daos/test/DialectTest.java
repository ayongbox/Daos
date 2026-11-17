package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.DdlPlan;
import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.QueryPlan;
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
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.engine.ddl.DefaultDdlCompiler;
import io.github.ayongbox.daos.runtime.engine.dialect.MysqlDialect;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialectTest {

    private static final String APP_ID = "app-1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private final MysqlDialect mysql = new MysqlDialect();
    private final PostgresDialect postgres = new PostgresDialect();
    private final SchemaNormalizer normalizer = new SchemaNormalizer();
    private final DefaultDdlCompiler ddlCompiler = new DefaultDdlCompiler();

    @Test
    void mysqlCreateTableInjectsPlatformColumnsAndIndexes() {
        SchemaTable orders = table("t1", "orders", List.of(
                text("t1", "f1", "title", FieldConstraints.defaults()),
                bool("t1", "f2", "paid"),
                text("t1", "f3", "no", unique())));

        DdlPlan plan = ddlCompiler.compile(normalize(orders), mysql);

        String createTable = plan.statements().get(0).sql();
        assertTrue(createTable.startsWith("CREATE TABLE `app_demo_orders`"));
        assertTrue(createTable.contains("`tenant_id` varchar(36) NOT NULL"));
        assertTrue(createTable.contains("`version` int NOT NULL DEFAULT 0"));
        assertTrue(createTable.contains("`deleted` tinyint(1) NOT NULL DEFAULT 0"));
        assertTrue(createTable.contains("`paid` tinyint(1)"));
        assertFalse(createTable.contains("`_ext`"), "PHYSICAL 表不应生成 _ext 列");

        String uniqueIndex = plan.statements().stream()
                .map(DdlPlan.Statement::sql)
                .filter(sql -> sql.startsWith("CREATE UNIQUE INDEX"))
                .findFirst()
                .orElseThrow();
        assertTrue(uniqueIndex.contains("(`tenant_id`, `no`)"));
    }

    @Test
    void postgresMapsTypesAndQuotesWithDoubleQuotes() {
        SchemaTable orders = table("t1", "orders", List.of(
                decimal("t1", "f1", "amount"),
                multiSelect("t1", "f2", "tags")));

        DdlPlan plan = ddlCompiler.compile(normalize(orders), postgres);

        String createTable = plan.statements().get(0).sql();
        assertTrue(createTable.startsWith("CREATE TABLE \"app_demo_orders\""));
        assertTrue(createTable.contains("\"amount\" numeric(20,6)"));
        assertTrue(createTable.contains("\"tags\" jsonb"));
        assertTrue(createTable.contains("\"deleted\" boolean NOT NULL DEFAULT FALSE"));
    }

    @Test
    void autoNumberIsStoredAsPhysicalColumn() {
        SchemaTable orders = table("t1", "orders", List.of(autoNumber("t1", "f1", "serial")));

        NormalizedSchema schema = normalize(orders);
        assertEquals(IrColumn.Storage.COLUMN,
                schema.requireTable("t1").requireColumn("serial").storage());
        assertTrue(ddlCompiler.compile(schema, mysql).statements().get(0).sql()
                .contains("`serial` varchar(64)"));
    }

    @Test
    void rendersSelectWithPlaceholdersAndEscapedLikePattern() {
        QueryPlan.Select plan = new QueryPlan.Select(
                "app_demo_orders",
                List.of(new QueryPlan.SelectItem("title", null)),
                FilterNode.and(
                        new FilterNode.Compare("title", ConditionOperator.CONTAINS, List.of("50%")),
                        new FilterNode.Compare("paid", ConditionOperator.EQ, List.of(true))),
                List.of(new QueryPlan.OrderItem("created_at", false)),
                20,
                0);

        SqlStatement statement = mysql.query().render(plan);

        assertEquals("SELECT `title` FROM `app_demo_orders`"
                + " WHERE (`title` LIKE ? ESCAPE '!' AND `paid` = ?)"
                + " ORDER BY `created_at` DESC LIMIT 20", statement.sql());
        assertEquals(List.of("%50!%%", true), statement.parameters());
    }

    @Test
    void postgresUsesIlikeForContains() {
        QueryPlan.Select plan = new QueryPlan.Select(
                "app_demo_orders",
                List.of(),
                new FilterNode.Compare("title", ConditionOperator.CONTAINS, List.of("abc")),
                List.of(),
                10,
                0);

        assertEquals("SELECT * FROM \"app_demo_orders\" WHERE \"title\" ILIKE ? ESCAPE '!' LIMIT 10",
                postgres.query().render(plan).sql());
    }

    @Test
    void rejectsSemanticOperatorBeforeRender() {
        QueryPlan.Select plan = new QueryPlan.Select(
                "app_demo_orders",
                List.of(),
                new FilterNode.Compare("owner_id", ConditionOperator.CURRENT_USER, List.of()),
                List.of(),
                10,
                0);

        assertThrows(UnsupportedOperationException.class, () -> mysql.query().render(plan));
    }

    @Test
    void postgresEmitsPartialIndexAndMysqlIgnoresWhere() {
        var pgIndex = new io.github.ayongbox.daos.core.pipeline.dialect.DdlDialect.IndexDdl(
                "idx_active", List.of("deleted"), false,
                io.github.ayongbox.daos.core.metadata.common.IndexType.BTREE, "deleted = 0");

        String pg = postgres.ddl().createIndex("app_demo_orders", pgIndex).sql();
        String my = mysql.ddl().createIndex("app_demo_orders", pgIndex).sql();

        assertTrue(pg.contains("WHERE deleted = 0"));
        assertFalse(my.contains("WHERE"));
    }

    @Test
    void rejectsIdentifierContainingQuoteChar() {
        assertThrows(IllegalArgumentException.class, () -> mysql.quote("a`b"));
        assertThrows(IllegalArgumentException.class, () -> postgres.quote("a\"b"));
    }

    private NormalizedSchema normalize(SchemaTable table) {
        return normalizer.normalize("demo", "snap-1", 1L, List.of(table));
    }

    private static FieldConstraints unique() {
        return new FieldConstraints(false, true, false, false, false, true,
                false, false, true, null, null, false, null);
    }

    private static SchemaField text(String tableId, String id, String code, FieldConstraints constraints) {
        return field(tableId, id, code, FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false), constraints);
    }

    private static SchemaField bool(String tableId, String id, String code) {
        return field(tableId, id, code, FieldLogicalType.BOOLEAN,
                new FieldConfig.BooleanConfig("是", "否", "switch", false), FieldConstraints.defaults());
    }

    private static SchemaField decimal(String tableId, String id, String code) {
        return field(tableId, id, code, FieldLogicalType.DECIMAL,
                new FieldConfig.DecimalConfig(20, 6, null, null, null, null, null, true),
                FieldConstraints.defaults());
    }

    private static SchemaField multiSelect(String tableId, String id, String code) {
        return field(tableId, id, code, FieldLogicalType.MULTI_SELECT,
                new FieldConfig.MultiSelectConfig(null, null, List.of(), "tag", 0, false, null),
                FieldConstraints.defaults());
    }

    private static SchemaField autoNumber(String tableId, String id, String code) {
        return field(tableId, id, code, FieldLogicalType.AUTO_NUMBER,
                new FieldConfig.AutoNumberConfig("PO", "yyyyMMdd", 4, "never", 1L, null),
                FieldConstraints.defaults());
    }

    private static SchemaField field(
            String tableId, String id, String code,
            FieldLogicalType logicalType, FieldConfig config, FieldConstraints constraints) {
        return new SchemaField(id, APP_ID, tableId, code, code, null,
                logicalType, config, constraints, FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaTable table(String id, String code, List<SchemaField> fields) {
        return new SchemaTable(id, APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                code, code, null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields, List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
