package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.FilterNode;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.QueryRequest;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.OnDeleteAction;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.SubjectType;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.engine.dialect.H2Dialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F00-05 金标准用例。
 *
 * <p>固定一套元数据与数据集，对查询层输出做逐值断言。用例只允许通过新增数据或新增
 * 断言演进，不允许为了通过而改断言——这正是金标准的意义：先发现行为变化，再决定是
 * 回归缺陷还是预期变更。</p>
 *
 * <p>数据集（订单表，单号即 id）：</p>
 * <pre>
 * o1  100  上海  阿里    租户 t1
 * o2  250  北京  字节    租户 t1
 * o3   50  上海  阿里    租户 t1
 * o4  999  上海  阿里    租户 t2
 * o5  300  北京  字节    租户 t1，已软删
 * </pre>
 */
class GoldenCaseTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";
    private static final String ORDERS = "t1";
    private static final String CUSTOMERS = "tc";

    private DataQueryService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_golden_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        DataAccessFacade dataAccess = new DataAccessFacade(dataSource);

        createSchema(dataAccess);
        insertDataset(dataAccess);

        InMemoryMetadataStore metadataStore = new InMemoryMetadataStore();
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        metadataStore.saveDraft(app());
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");

        service = new DataQueryService(snapshotStore, dataAccess, new H2Dialect());
    }

    @Test
    void listReturnsTenantScopedUndeletedRowsInOrder() {
        QueryRequest request = new QueryRequest(
                List.of("amount", "city", "customer_ref"),
                null,
                List.of(new QueryRequest.SortSpec("amount", SortDirection.DESC)),
                1, 20);

        List<Map<String, Object>> rows = service.query(APP_ID, ORDERS, request, context("t1"));

        assertEquals(List.of("o2", "o1", "o3"), ids(rows));
        assertEquals(250, amount(rows.get(0)));
        assertEquals(100, amount(rows.get(1)));
        assertEquals(50, amount(rows.get(2)));
        assertEquals("c2", rows.get(0).get("customer_ref"));
    }

    @Test
    void filterByEqualsAndRange() {
        QueryRequest shanghai = new QueryRequest(
                List.of("amount", "city"),
                new FilterNode.Compare("city", ConditionOperator.EQ, List.of("上海")),
                List.of(new QueryRequest.SortSpec("amount", SortDirection.ASC)),
                1, 20);

        assertEquals(List.of("o3", "o1"),
                ids(service.query(APP_ID, ORDERS, shanghai, context("t1"))));

        QueryRequest range = new QueryRequest(
                List.of("amount"),
                FilterNode.and(
                        new FilterNode.Compare("amount", ConditionOperator.GTE, List.of(100)),
                        new FilterNode.Compare("amount", ConditionOperator.LT, List.of(300))),
                List.of(new QueryRequest.SortSpec("amount", SortDirection.ASC)),
                1, 20);

        assertEquals(List.of("o1", "o2"),
                ids(service.query(APP_ID, ORDERS, range, context("t1"))));
    }

    @Test
    void filterByContains() {
        QueryRequest request = new QueryRequest(
                List.of("city"),
                new FilterNode.Compare("city", ConditionOperator.CONTAINS, List.of("北")),
                List.of(new QueryRequest.SortSpec("amount", SortDirection.DESC)),
                1, 20);

        assertEquals(List.of("o2"),
                ids(service.query(APP_ID, ORDERS, request, context("t1"))));
    }

    @Test
    void paginationAndCount() {
        QueryRequest page = new QueryRequest(
                List.of("amount"),
                null,
                List.of(new QueryRequest.SortSpec("amount", SortDirection.DESC)),
                2, 1);

        assertEquals(List.of("o1"),
                ids(service.query(APP_ID, ORDERS, page, context("t1"))));
        assertEquals(3, service.count(APP_ID, ORDERS, page, context("t1")));
    }

    @Test
    void aggregateByCityWithHaving() {
        QueryRequest request = new QueryRequest(
                List.of(),
                null,
                List.of(new QueryRequest.SortSpec("city", SortDirection.ASC)),
                1, 20,
                List.of("city"),
                List.of(
                        new QueryRequest.AggregateSpec(
                                io.github.ayongbox.daos.core.ir.AggregateFunction.COUNT,
                                null, "记录数"),
                        new QueryRequest.AggregateSpec(
                                io.github.ayongbox.daos.core.ir.AggregateFunction.SUM,
                                "amount", "金额合计")),
                List.of(new QueryRequest.HavingSpec(
                        "金额合计", ConditionOperator.GTE, 200)));

        List<Map<String, Object>> rows = service.query(APP_ID, ORDERS, request, context("t1"));

        assertEquals(1, rows.size());
        assertEquals("北京", rows.get(0).get("city"));
        assertEquals(1L, ((Number) rows.get(0).get("记录数")).longValue());
        assertEquals(0, new BigDecimal("250.00").compareTo(decimal(rows.get(0).get("金额合计"))));
    }

    @Test
    void relationDisplayResolvedInBatch() {
        QueryRequest request = new QueryRequest(
                List.of("amount", "customer_ref"),
                null,
                List.of(new QueryRequest.SortSpec("amount", SortDirection.DESC)),
                1, 20);

        List<Map<String, Object>> rows = service.query(APP_ID, ORDERS, request, context("t1"));

        assertEquals("字节", rows.get(0).get("customer_ref_display"));
        assertEquals("阿里", rows.get(1).get("customer_ref_display"));
        assertEquals("阿里", rows.get(2).get("customer_ref_display"));
    }

    @Test
    void crossTenantContextOnlySeesOwnRows() {
        QueryRequest request = new QueryRequest(
                List.of("amount"),
                null,
                List.of(new QueryRequest.SortSpec("amount", SortDirection.DESC)),
                1, 20);

        List<Map<String, Object>> rows = service.query(APP_ID, ORDERS, request, context("t2"));

        assertEquals(List.of("o4"), ids(rows));
        assertEquals(999, amount(rows.get(0)));
    }

    // ---------------------------------------------------------------- 数据与元数据

    private static void createSchema(DataAccessFacade dataAccess) {
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_customers\" (\"id\" varchar(26) PRIMARY KEY, "
                        + "\"tenant_id\" varchar(36), \"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, \"name\" varchar(255))"));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_orders\" (\"id\" varchar(26) PRIMARY KEY, "
                        + "\"tenant_id\" varchar(36), \"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, \"amount\" numeric(20,2), "
                        + "\"city\" varchar(64), \"customer_ref\" varchar(26))"));
    }

    private static void insertDataset(DataAccessFacade dataAccess) {
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_customers\" (\"id\",\"tenant_id\",\"deleted\",\"name\") VALUES (?,?,?,?)",
                "c1", "t1", false, "阿里"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_customers\" (\"id\",\"tenant_id\",\"deleted\",\"name\") VALUES (?,?,?,?)",
                "c2", "t1", false, "字节"));

        insertOrder(dataAccess, "o1", "t1", false, 100, "上海", "c1");
        insertOrder(dataAccess, "o2", "t1", false, 250, "北京", "c2");
        insertOrder(dataAccess, "o3", "t1", false, 50, "上海", "c1");
        insertOrder(dataAccess, "o4", "t2", false, 999, "上海", "c1");
        insertOrder(dataAccess, "o5", "t1", true, 300, "北京", "c2");
    }

    private static void insertOrder(
            DataAccessFacade dataAccess,
            String id, String tenant, boolean deleted, int amount, String city, String customer) {
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_orders\""
                        + " (\"id\",\"tenant_id\",\"deleted\",\"amount\",\"city\",\"customer_ref\")"
                        + " VALUES (?,?,?,?,?,?)",
                id, tenant, deleted, new BigDecimal(amount), city, customer));
    }

    private static PermissionContext context(String tenant) {
        return new PermissionContext(
                tenant, "user-" + tenant, List.of(), List.of(),
                List.of(readPermission(ORDERS, "orders"), readPermission(CUSTOMERS, "customers")));
    }

    private static SchemaPermission readPermission(String tableId, String tableCode) {
        return new SchemaPermission(
                "p-" + tableCode, APP_ID, "read-" + tableCode, "读取" + tableCode, null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, tableCode, List.of()),
                List.of(ActionType.READ), EffectType.ALLOW,
                null, null, FieldMask.empty(), 100, true, null, null, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaApp app() {
        SchemaTable customers = table(CUSTOMERS, "customers", "客户", List.of(
                field("f1", CUSTOMERS, "name", "名称", FieldLogicalType.TEXT_SHORT,
                        new FieldConfig.TextShortConfig(255, 0, null, null, false), true)));

        SchemaTable orders = table(ORDERS, "orders", "订单", List.of(
                field("f1", ORDERS, "amount", "金额", FieldLogicalType.DECIMAL,
                        new FieldConfig.DecimalConfig(
                                18, 2, null, null, null, "#,##0.00", "HALF_UP", true), false),
                field("f2", ORDERS, "city", "城市", FieldLogicalType.TEXT_SHORT,
                        new FieldConfig.TextShortConfig(64, 0, null, null, false), false),
                field("f3", ORDERS, "customer_ref", "客户", FieldLogicalType.RELATION,
                        new FieldConfig.RelationConfig(
                                CUSTOMERS, "f1", false, 0, null,
                                OnDeleteAction.RESTRICT, false, "select", null), false)));

        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(customers, orders), List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaTable table(
            String id, String code, String displayName, List<SchemaField> fields) {
        return new SchemaTable(
                id, APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                code, displayName, null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields,
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField field(
            String id,
            String tableId,
            String code,
            String displayName,
            FieldLogicalType type,
            FieldConfig config,
            boolean nameField) {
        return new SchemaField(
                id, APP_ID, tableId, code, displayName, null, type, config,
                new FieldConstraints(false, false, false, false, true, true,
                        false, false, true, null, null, false, null),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, nameField, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    // ---------------------------------------------------------------- 断言工具

    private static List<String> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> String.valueOf(row.get("id"))).toList();
    }

    private static int amount(Map<String, Object> row) {
        return decimal(row.get("amount")).intValueExact();
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }
}
