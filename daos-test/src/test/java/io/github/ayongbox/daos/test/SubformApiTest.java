package io.github.ayongbox.daos.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.ir.SqlStatement;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
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
import io.github.ayongbox.daos.core.metadata.form.FormComponent;
import io.github.ayongbox.daos.core.metadata.form.FormField;
import io.github.ayongbox.daos.core.metadata.form.FormType;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.exec.DataQueryService;
import io.github.ayongbox.daos.runtime.exec.DataWriteService;
import io.github.ayongbox.daos.runtime.identity.InMemoryIdentityStore;
import io.github.ayongbox.daos.runtime.identity.PermissionContextFactory;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.runtime.relation.RelationService;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;
import io.github.ayongbox.daos.runtime.subform.SubformService;
import io.github.ayongbox.daos.web.auth.DaosPermissionChecker;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;
import io.github.ayongbox.daos.web.form.FormRecordController;
import io.github.ayongbox.daos.web.support.ApiExceptionHandler;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubformApiTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TENANT = "t1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";
    private static final String BASE = "/api/apps/" + APP_ID + "/forms/basic/records";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mvc;
    private Session session;
    private DataAccessFacade dataAccess;
    private MetadataStore metadataStore;
    private SnapshotStore snapshotStore;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_subform_api;DB_CLOSE_DELAY=-1");
        DataAccessFacade dataAccess = new DataAccessFacade(dataSource);
        this.dataAccess = dataAccess;
        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"app_demo_orders\""));
        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"app_demo_order_items\""));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_orders\" ("
                        + "\"id\" varchar(26) PRIMARY KEY, \"tenant_id\" varchar(36) NOT NULL, "
                        + "\"created_by\" varchar(36), \"created_at\" timestamp, "
                        + "\"updated_by\" varchar(36), \"updated_at\" timestamp, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, "
                        + "\"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"title\" varchar(255), \"total_qty\" decimal(20,2))"));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_order_items\" ("
                        + "\"id\" varchar(26) PRIMARY KEY, \"tenant_id\" varchar(36) NOT NULL, "
                        + "\"created_by\" varchar(36), \"created_at\" timestamp, "
                        + "\"updated_by\" varchar(36), \"updated_at\" timestamp, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, "
                        + "\"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"sku\" varchar(255), \"qty\" decimal(20,2), "
                        + "\"order_ref\" varchar(26))"));

        metadataStore = new InMemoryMetadataStore();
        snapshotStore = new InMemorySnapshotStore();
        publish(true);

        OrgMember member = new OrgMember(
                "m-1", TENANT, "alice", "Alice", null, null, null, MemberStatus.ACTIVE,
                Map.of(), "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        RoleDefinition role = new RoleDefinition(
                "r-1", TENANT, null, "editors", "编辑", RoleScope.PLATFORM, null, true, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        RoleBinding binding = new RoleBinding(
                "b-1", TENANT, "r-1", "m-1", Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        InMemoryIdentityStore identityStore = new InMemoryIdentityStore(
                null, List.of(member), List.of(role), List.of(binding));

        AuditLog auditLog = new AuditLog(new InMemoryAuditStore());
        SessionPermissionService permissionService = new SessionPermissionService(
                snapshotStore, new InMemoryMetadataStore(),
                new PermissionContextFactory(identityStore));
        DataQueryService queryService =
                new DataQueryService(snapshotStore, dataAccess, new PostgresDialect(), auditLog);
        DataWriteService writeService =
                new DataWriteService(snapshotStore, dataAccess, new PostgresDialect(), auditLog);
        RelationService relationService = new RelationService(snapshotStore, queryService);
        SubformService subformService = new SubformService(
                snapshotStore, dataAccess, queryService, writeService, relationService);
        FormRecordController controller = new FormRecordController(
                subformService, permissionService, new DaosPermissionChecker(permissionService));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        Instant now = Instant.parse(TIMESTAMP);
        session = new Session(
                "s-1", TENANT, "acc-1", "alice", "m-1", "hash",
                now, now.plusSeconds(3600), null, null);
    }

    @Test
    void createMasterAndSubformRowsInOneRequest() throws Exception {
        String id = createOrder("订单A");

        mvc.perform(get(BASE + "/" + id).requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record.title").value("订单A"))
                .andExpect(jsonPath("$.data.record.total_qty").value(3.0))
                .andExpect(jsonPath("$.data.subforms['sf-1'].length()").value(2));
    }

    @Test
    void updateReconcilesSubformRows() throws Exception {
        String id = createOrder("订单A");
        JsonNode detail = getDetail(id);
        String existingChildId = detail.at("/data/subforms/sf-1/0/id").asText();
        long version = detail.at("/data/record/version").asLong();

        Map<String, Object> body = Map.of(
                "values", Map.of("title", "订单B"),
                "subforms", Map.of("sf-1", List.of(
                        Map.of("id", existingChildId, "sku", "C", "qty", "3"),
                        Map.of("sku", "D", "qty", "4"))));
        mvc.perform(put(BASE + "/" + id).param("version", String.valueOf(version))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affected").value(1));

        JsonNode updated = getDetail(id);
        assertEquals("订单B", updated.at("/data/record/title").asText());
        assertEquals(7.0, updated.at("/data/record/total_qty").asDouble());
        JsonNode rows = updated.at("/data/subforms/sf-1");
        assertEquals(2, rows.size());
        Set<String> skus = new HashSet<>();
        rows.forEach(row -> skus.add(row.get("sku").asText()));
        assertEquals(Set.of("C", "D"), skus);
    }

    @Test
    void deletingSubformRowRecalculatesRollup() throws Exception {
        String id = createOrder("订单A");
        JsonNode detail = getDetail(id);
        JsonNode first = detail.at("/data/subforms/sf-1/0");
        Map<String, Object> body = Map.of(
                "values", Map.of(),
                "subforms", Map.of("sf-1", List.of(Map.of(
                        "id", first.get("id").asText(),
                        "version", first.get("version").asLong(),
                        "sku", "A",
                        "qty", "1"))));

        mvc.perform(put(BASE + "/" + id)
                        .param("version", detail.at("/data/record/version").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr(attr(), session))
                .andExpect(status().isOk());

        JsonNode updated = getDetail(id);
        assertEquals(1.0, updated.at("/data/record/total_qty").asDouble());
        assertEquals(1, updated.at("/data/subforms/sf-1").size());
    }

    @Test
    void missingMasterUpdatePermissionRollsBackChildAndRollup() throws Exception {
        String id = createOrder("订单A");
        publish(true, false);
        JsonNode detail = getDetail(id);
        List<Map<String, Object>> submitted = new java.util.ArrayList<>();
        for (JsonNode row : detail.at("/data/subforms/sf-1")) {
            submitted.add(Map.of(
                    "id", row.get("id").asText(),
                    "version", row.get("version").asLong(),
                    "sku", row.get("sku").asText(),
                    "qty", "A".equals(row.get("sku").asText()) ? "9" : row.get("qty").asText()));
        }
        Map<String, Object> body = Map.of(
                "values", Map.of(), "subforms", Map.of("sf-1", submitted));

        mvc.perform(put(BASE + "/" + id)
                        .param("version", detail.at("/data/record/version").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr(attr(), session))
                .andExpect(status().isForbidden());

        JsonNode unchanged = getDetail(id);
        assertEquals(3.0, unchanged.at("/data/record/total_qty").asDouble());
        Set<Double> quantities = new HashSet<>();
        unchanged.at("/data/subforms/sf-1").forEach(row -> quantities.add(row.get("qty").asDouble()));
        assertEquals(Set.of(1.0, 2.0), quantities);
    }

    @Test
    void deleteCascadesSubformRows() throws Exception {
        String id = createOrder("订单A");

        long version = getDetail(id).at("/data/record/version").asLong();
        mvc.perform(delete(BASE + "/" + id).param("version", String.valueOf(version))
                        .requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affected").value(1));

        assertEquals(0, liveChildRows(id));
        assertEquals(0, dataAccess.query(new SqlStatement(
                "SELECT count(*) AS \"c\" FROM \"app_demo_orders\" "
                        + "WHERE \"id\" = ? AND \"deleted\" = FALSE",
                List.of(id))).stream()
                .mapToInt(row -> ((Number) row.get("c")).intValue())
                .sum());
    }

    @Test
    void missingChildPermissionRollsBackMaster() throws Exception {
        publish(false);

        mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderPayload("订单X")))
                        .requestAttr(attr(), session))
                .andExpect(status().isForbidden());

        List<Map<String, Object>> rows = dataAccess.query(new SqlStatement(
                "SELECT count(*) AS \"c\" FROM \"app_demo_orders\" "
                        + "WHERE \"title\" = ? AND \"deleted\" = FALSE",
                List.of("订单X")));
        assertEquals(0, ((Number) rows.get(0).get("c")).intValue());
    }

    private String createOrder(String title) throws Exception {
        MvcResult result = mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderPayload(title)))
                        .requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data").get("id").asText();
    }

    private JsonNode getDetail(String id) throws Exception {
        MvcResult result = mvc.perform(get(BASE + "/" + id).requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Map<String, Object> orderPayload(String title) {
        return Map.of(
                "values", Map.of("title", title),
                "subforms", Map.of("sf-1", List.of(
                        Map.of("sku", "A", "qty", "1"),
                        Map.of("sku", "B", "qty", "2"))));
    }

    private int liveChildRows(String orderId) {
        List<Map<String, Object>> rows = dataAccess.query(new SqlStatement(
                "SELECT count(*) AS \"c\" FROM \"app_demo_order_items\" "
                        + "WHERE \"order_ref\" = ? AND \"deleted\" = FALSE",
                List.of(orderId)));
        return ((Number) rows.get(0).get("c")).intValue();
    }

    private static String attr() {
        return SessionAuthInterceptor.SESSION_ATTRIBUTE;
    }

    private void publish(boolean childCreatable) {
        publish(childCreatable, true);
    }

    private void publish(boolean childCreatable, boolean parentUpdatable) {
        metadataStore.saveDraft(app(childCreatable, parentUpdatable));
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");
    }

    private static SchemaApp app(boolean childCreatable, boolean parentUpdatable) {
        SchemaField title = field("f-1", "t1", "title", FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false));
        SchemaField totalQty = field("f-total", "t1", "total_qty", FieldLogicalType.ROLLUP,
                new FieldConfig.RollupConfig("f-5", "f-4", "sum", null, 2, true));
        SchemaField sku = field("f-3", "t2", "sku", FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false));
        SchemaField qty = field("f-4", "t2", "qty", FieldLogicalType.DECIMAL,
                new FieldConfig.DecimalConfig(20, 2, null, null, null, null, null, false));
        SchemaField orderRef = field("f-5", "t2", "order_ref", FieldLogicalType.RELATION,
                new FieldConfig.RelationConfig(
                        "t1", null, false, 0, null, null, false, null, null));

        SchemaTable orders = table("t1", "orders", "订单", List.of(title, totalQty));
        SchemaTable items = table("t2", "order_items", "订单明细", List.of(sku, qty, orderRef));

        List<SchemaPermission> permissions = new java.util.ArrayList<>();
        List<ActionType> parentActions = new java.util.ArrayList<>(List.of(
                ActionType.READ, ActionType.CREATE, ActionType.DELETE));
        if (parentUpdatable) {
            parentActions.add(ActionType.UPDATE);
        }
        permissions.add(permission("p-1", "orders", parentActions));
        if (childCreatable) {
            permissions.add(permission("p-2", "order_items", List.of(
                    ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE)));
        }

        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(orders, items), List.of(form()), permissions, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaForm form() {
        FormField title = input("n-1", "f-1", "标题", 24, 0);
        FormField totalQty = input("n-total", "f-total", "数量合计", 24, 1);
        FormField subform = new FormField(
                "sf-1", null, FormComponent.SUBFORM, "明细", 24, 0, 1, null, null, null,
                false, false, false, null, null, Map.of(), Map.of(), List.of(),
                "t2", List.of(input("sf-f3", "f-3", "SKU", 12, 0),
                        input("sf-f4", "f-4", "数量", 12, 1)),
                0, 200, true, true, true, true, false, Map.of(),
                null, null, false, false, 2);
        return new SchemaForm(
                "form-1", APP_ID, "t1", "basic", "订单表单", null, FormType.RECORD, Map.of(),
                List.of(title, totalQty, subform), List.of(), List.of(), List.of(),
                MetadataStatus.DRAFT, 1, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static FormField input(String id, String fieldId, String label, int span, int sort) {
        return new FormField(
                id, fieldId, FormComponent.INPUT, label, span, 0, sort, null, null, null,
                false, false, false, null, null, Map.of(), Map.of(), List.of(),
                null, List.of(), 0, 0, true, true, true, true, false, Map.of(),
                null, null, false, false, 2);
    }

    private static SchemaPermission permission(
            String id, String tableCode, List<ActionType> actions) {
        return new SchemaPermission(
                id, APP_ID, "perm-" + tableCode, "权限 " + tableCode, null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, tableCode, List.of()),
                actions, EffectType.ALLOW, null, null, null,
                100, true, null, null, false, Map.of(),
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
                List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField field(
            String id, String tableId, String code, FieldLogicalType type, FieldConfig config) {
        return new SchemaField(
                id, APP_ID, tableId, code, code, null, type, config,
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
