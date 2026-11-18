package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.AggregateFunction;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ConditionOperator;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
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
import io.github.ayongbox.daos.core.metadata.menu.MenuType;
import io.github.ayongbox.daos.core.metadata.menu.SchemaMenu;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.core.metadata.view.SchemaView;
import io.github.ayongbox.daos.core.metadata.view.ViewAggregation;
import io.github.ayongbox.daos.core.metadata.view.ViewColumn;
import io.github.ayongbox.daos.core.metadata.view.ViewFilter;
import io.github.ayongbox.daos.core.metadata.view.ViewHaving;
import io.github.ayongbox.daos.core.metadata.view.ViewSort;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.engine.dialect.H2Dialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.menu.MenuService;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;
import io.github.ayongbox.daos.runtime.view.ViewService;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewServiceTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private SnapshotStore snapshotStore;
    private ViewService viewService;
    private MenuService menuService;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_view;DB_CLOSE_DELAY=-1");
        DataAccessFacade dataAccess = new DataAccessFacade(dataSource);
        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"app_demo_users\""));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_users\" (\"id\" varchar(26) PRIMARY KEY, "
                        + "\"tenant_id\" varchar(36), \"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, "
                        + "\"name\" varchar(255), \"city\" varchar(255), "
                        + "\"amount\" numeric(12,2))"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_users\" (\"id\", \"tenant_id\", \"deleted\", \"name\", \"city\", \"amount\") "
                        + "VALUES ('1', 't1', false, 'alpha', '上海', 5)"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_users\" (\"id\", \"tenant_id\", \"deleted\", \"name\", \"city\", \"amount\") "
                        + "VALUES ('2', 't1', false, 'beta', '北京', 10)"));
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_users\" (\"id\", \"tenant_id\", \"deleted\", \"name\", \"city\", \"amount\") "
                        + "VALUES ('3', 't1', false, 'gamma', '北京', 20)"));

        MetadataStore metadataStore = new InMemoryMetadataStore();
        snapshotStore = new InMemorySnapshotStore();
        metadataStore.saveDraft(app());
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");

        DataQueryService queryService = new DataQueryService(
                snapshotStore, dataAccess, new H2Dialect(), new AuditLog(new InMemoryAuditStore()));
        viewService = new ViewService(snapshotStore, queryService);
        menuService = new MenuService(snapshotStore);
    }

    @Test
    void rendersViewColumnsFiltersAndSorts() {
        ViewService.ViewDetail detail = viewService.render(APP_ID, "users_by_city");

        assertEquals("users", detail.tableCode());
        assertEquals(10, detail.pageSize());
        assertEquals(2, detail.columns().size());
        assertEquals("name", detail.columns().get(0).fieldCode());
        assertFalse(detail.columns().get(1).visible());
        assertEquals("city", detail.filters().get(0).fieldCode());
        assertEquals(ConditionOperator.EQ, detail.filters().get(0).operator());
        assertEquals("北京", detail.filters().get(0).value());
        assertEquals("name", detail.sorts().get(0).fieldCode());
        assertEquals("DESC", detail.sorts().get(0).direction());
    }

    @Test
    void queriesRecordsWithSavedFilterAndSort() {
        ViewService.ViewPage page = viewService.query(APP_ID, "users_by_city", 1, 0, context());

        assertEquals(2, page.total());
        assertEquals(2, page.records().size());
        assertEquals("gamma", page.records().get(0).get("name"));
        assertEquals("beta", page.records().get(1).get("name"));
    }

    @Test
    void listsOnlyVisibleMenusSortedByOrder() {
        List<MenuService.MenuSummary> menus = menuService.listMenus(APP_ID);

        assertEquals(2, menus.size());
        assertEquals("用户表", menus.get(0).displayName());
        assertEquals("TABLE", menus.get(0).menuType());
        assertEquals("users", menus.get(0).targetCode());
        assertEquals("按城市看客户", menus.get(1).displayName());
        assertEquals("VIEW", menus.get(1).menuType());
        assertEquals("users_by_city", menus.get(1).targetCode());
    }

    @Test
    void aggregatesRecordsByGroupWithCountAndSum() {
        ViewService.ViewAggregatePage page = viewService.aggregate(
                APP_ID, "users_amount_by_city", 1, 0, context());

        assertEquals(2, page.total());
        assertEquals(2, page.rows().size());
        Map<String, Object> beijing = page.rows().stream()
                .filter(row -> "北京".equals(row.get("city")))
                .findFirst()
                .orElseThrow();
        assertEquals(2L, ((Number) beijing.get("记录数")).longValue());
        assertEquals(0, new java.math.BigDecimal("30")
                .compareTo((java.math.BigDecimal) beijing.get("金额合计")));
    }

    @Test
    void summarizesAllRecordsForBoardKpi() {
        Map<String, Object> summary = viewService.summary(
                APP_ID, "users_amount_by_city", context());

        assertEquals(3L, ((Number) summary.get("记录数")).longValue());
        assertEquals(0, new java.math.BigDecimal("35")
                .compareTo((java.math.BigDecimal) summary.get("金额合计")));
    }

    @Test
    void sortsAggregateRowsByAggregationColumn() {
        ViewService.ViewAggregatePage page = viewService.aggregate(
                APP_ID, "users_amount_by_city", 1, 0, context());

        assertEquals("北京", page.rows().get(0).get("city"));
        assertEquals("上海", page.rows().get(1).get("city"));
    }

    @Test
    void appliesHavingToGroupedAggregation() {
        ViewService.ViewAggregatePage page = viewService.aggregate(
                APP_ID, "users_big_cities", 1, 0, context());

        assertEquals(1, page.total());
        assertEquals(1, page.rows().size());
        assertEquals("北京", page.rows().get(0).get("city"));
        assertEquals(2L, ((Number) page.rows().get(0).get("记录数")).longValue());
    }

    @Test
    void returnsEmptyViewListWhenAppIsNotPublished() {
        assertTrue(viewService.listViews("unpublished-app").isEmpty());
    }

    @Test
    void rendersAggregationConfiguration() {
        ViewService.ViewDetail detail = viewService.render(APP_ID, "users_amount_by_city");

        assertEquals(1, detail.groupBy().size());
        assertEquals("city", detail.groupBy().get(0).fieldCode());
        assertEquals(2, detail.aggregations().size());
        assertEquals("记录数", detail.aggregations().get(0).displayName());
        assertEquals("sum", detail.aggregations().get(1).function().code());
        assertEquals("amount", detail.aggregations().get(1).fieldCode());
    }

    @Test
    void viewsAndMenusArePublishedIntoSnapshot() {
        SchemaSnapshot active = snapshotStore.findActive(APP_ID).orElseThrow();

        assertEquals(3, active.app().views().size());
        assertEquals(3, active.app().menus().size());
        assertTrue(active.contentHash().length() > 0);
    }

    private static PermissionContext context() {
        return new PermissionContext(
                "t1", "user-1", List.of(), List.of(), List.of(permission()));
    }

    private static SchemaPermission permission() {
        return new SchemaPermission(
                "p1", APP_ID, "read-users", "读取用户", null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, "users", List.of()),
                List.of(ActionType.READ), EffectType.ALLOW, null, null, null,
                100, true, null, null, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaApp app() {
        SchemaField name = field("f1", "name");
        SchemaField city = field("f2", "city");
        SchemaField amount = numberField("f3", "amount");

        SchemaTable users = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), List.of(name, city, amount),
                List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        SchemaView view = new SchemaView(
                "v1", APP_ID, "t1", "users_by_city", "按城市看客户", null,
                io.github.ayongbox.daos.core.metadata.view.ViewType.GRID,
                List.of(
                        new ViewColumn("f1", true, null, 0),
                        new ViewColumn("f2", false, 160, 1)),
                List.of(new ViewFilter("f2", ConditionOperator.EQ, "北京")),
                List.of(new ViewSort("f1", SortDirection.DESC)),
                10, MetadataStatus.DRAFT, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        SchemaView aggregateView = new SchemaView(
                "v2", APP_ID, "t1", "users_amount_by_city", "城市金额汇总", null,
                io.github.ayongbox.daos.core.metadata.view.ViewType.GRID,
                List.of(), List.of(),
                List.of(new ViewSort(null, "金额合计", SortDirection.DESC)),
                List.of("f2"),
                List.of(
                        new ViewAggregation(AggregateFunction.COUNT, null, "记录数"),
                        new ViewAggregation(AggregateFunction.SUM, "f3", "金额合计")),
                List.of(),
                20, MetadataStatus.DRAFT, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        SchemaView havingView = new SchemaView(
                "v3", APP_ID, "t1", "users_big_cities", "大城市汇总", null,
                io.github.ayongbox.daos.core.metadata.view.ViewType.GRID,
                List.of(), List.of(),
                List.of(new ViewSort(null, "金额合计", SortDirection.DESC)),
                List.of("f2"),
                List.of(
                        new ViewAggregation(AggregateFunction.COUNT, null, "记录数"),
                        new ViewAggregation(AggregateFunction.SUM, "f3", "金额合计")),
                List.of(new ViewHaving("记录数", ConditionOperator.GTE, 2)),
                20, MetadataStatus.DRAFT, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        SchemaMenu tableMenu = new SchemaMenu(
                "m1", APP_ID, "users", "用户表", null, MenuType.TABLE, "t1", 0, true,
                MetadataStatus.DRAFT, Map.of(), "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        SchemaMenu hiddenMenu = new SchemaMenu(
                "m2", APP_ID, "hidden", "隐藏菜单", null, MenuType.VIEW, "v1", 1, false,
                MetadataStatus.DRAFT, Map.of(), "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        SchemaMenu viewMenu = new SchemaMenu(
                "m3", APP_ID, "customers", "按城市看客户", null, MenuType.VIEW, "v1", 2, true,
                MetadataStatus.DRAFT, Map.of(), "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(users), List.of(), List.of(view, aggregateView, havingView),
                List.of(tableMenu, hiddenMenu, viewMenu), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField numberField(String id, String code) {
        return new SchemaField(
                id, APP_ID, "t1", code, code, null,
                FieldLogicalType.NUMBER,
                new FieldConfig.NumberConfig(null, null, 1, 2, null, false),
                new FieldConstraints(false, false, false, false, true, true,
                        false, false, true, null, null, false, null),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 1, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
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
