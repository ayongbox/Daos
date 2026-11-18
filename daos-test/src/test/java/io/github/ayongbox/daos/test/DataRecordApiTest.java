package io.github.ayongbox.daos.test;

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
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
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
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;
import io.github.ayongbox.daos.web.auth.DaosPermissionChecker;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;
import io.github.ayongbox.daos.web.data.DataRecordController;
import io.github.ayongbox.daos.web.support.ApiExceptionHandler;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DataRecordApiTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TENANT = "t1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";
    private static final String BASE = "/api/apps/" + APP_ID + "/tables/users/records";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mvc;
    private Session session;
    private DataAccessFacade dataAccess;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_records_api;DB_CLOSE_DELAY=-1");
        DataAccessFacade dataAccess = new DataAccessFacade(dataSource);
        this.dataAccess = dataAccess;
        dataAccess.update(SqlStatement.of("DROP TABLE IF EXISTS \"app_demo_users\""));
        dataAccess.update(SqlStatement.of(
                "CREATE TABLE \"app_demo_users\" ("
                        + "\"id\" varchar(26) PRIMARY KEY, \"tenant_id\" varchar(36) NOT NULL, "
                        + "\"created_by\" varchar(36), \"created_at\" timestamp, "
                        + "\"updated_by\" varchar(36), \"updated_at\" timestamp, "
                        + "\"version\" bigint NOT NULL DEFAULT 0, "
                        + "\"deleted\" boolean NOT NULL DEFAULT FALSE, "
                        + "\"title\" varchar(255), \"owner\" varchar(36))"));

        MetadataStore metadataStore = new InMemoryMetadataStore();
        SnapshotStore snapshotStore = new InMemorySnapshotStore();
        metadataStore.saveDraft(app());
        new PublishService(metadataStore, snapshotStore).publish(APP_ID, "tester");

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
        SessionPermissionService permissionService =
                new SessionPermissionService(
                        snapshotStore, new InMemoryMetadataStore(),
                        new PermissionContextFactory(identityStore));
        DataRecordController controller = new DataRecordController(
                new DataQueryService(snapshotStore, dataAccess, new PostgresDialect(), auditLog),
                new DataWriteService(snapshotStore, dataAccess, new PostgresDialect(), auditLog),
                permissionService,
                new DaosPermissionChecker(permissionService),
                snapshotStore);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        Instant now = Instant.parse(TIMESTAMP);
        session = new Session(
                "s-1", TENANT, "acc-1", "alice", "m-1", "hash",
                now, now.plusSeconds(3600), null, null);
    }

    @Test
    void createThenQueryUsesSessionTenant() throws Exception {
        String id = create("alpha");

        mvc.perform(get(BASE).param("fields", "title").requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(id))
                .andExpect(jsonPath("$.data.records[0].title").value("alpha"));
    }

    @Test
    void updateAndDeleteRespectVersion() throws Exception {
        String id = create("alpha");

        mvc.perform(put(BASE + "/" + id).param("version", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"beta\"}")
                        .requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affected").value(1));

        mvc.perform(delete(BASE + "/" + id).param("version", "1").requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affected").value(1));

        mvc.perform(get(BASE).param("fields", "title").requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void staleVersionConflicts() throws Exception {
        String id = create("alpha");

        mvc.perform(put(BASE + "/" + id).param("version", "9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"beta\"}")
                        .requestAttr(attr(), session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void countRespectsTenantIsolation() throws Exception {
        dataAccess.update(SqlStatement.of(
                "INSERT INTO \"app_demo_users\" (\"id\", \"tenant_id\", \"deleted\", \"version\", \"title\") "
                        + "VALUES ('r-2', 't2', FALSE, 0, 'other')"));

        mvc.perform(get(BASE).param("fields", "title").requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void unknownTableIsBadRequest() throws Exception {
        mvc.perform(get("/api/apps/" + APP_ID + "/tables/missing/records")
                        .requestAttr(attr(), session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    private String create(String title) throws Exception {
        MvcResult result = mvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"owner\":\"m-1\"}")
                        .requestAttr(attr(), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    private static String attr() {
        return SessionAuthInterceptor.SESSION_ATTRIBUTE;
    }

    private static SchemaApp app() {
        SchemaField title = field("f-1", "title");
        SchemaField owner = field("f-2", "owner");

        SchemaTable users = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), List.of(title, owner),
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        SchemaPermission editor = new SchemaPermission(
                "p-1", APP_ID, "edit-users", "编辑用户", null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, APP_CODE, "users", List.of()),
                List.of(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE),
                EffectType.ALLOW, null, null, null,
                100, true, null, null, false, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                List.of(users), List.of(), List.of(editor), Map.of(),
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
