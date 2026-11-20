package io.github.ayongbox.daos.test;

import io.github.ayongbox.baseon.core.context.SecurityUtil;
import io.github.ayongbox.baseon.core.model.LoginUser;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
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
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.runtime.app.AppService;
import io.github.ayongbox.daos.runtime.engine.SchemaEvolutionService;
import io.github.ayongbox.daos.runtime.engine.ddl.DefaultMigrationCompiler;
import io.github.ayongbox.daos.runtime.engine.dialect.H2Dialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.identity.InMemoryIdentityStore;
import io.github.ayongbox.daos.runtime.identity.PermissionContextFactory;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemoryMigrationStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;
import io.github.ayongbox.daos.web.auth.DaosPermissionChecker;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;
import io.github.ayongbox.daos.web.schema.ReleaseController;
import io.github.ayongbox.daos.web.schema.SchemaEvolutionController;
import io.github.ayongbox.daos.web.support.ApiExceptionHandler;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 发布版本管理接口的集成测试：带发布说明的多版本发布、版本列表倒序与生效标记、回滚到任意历史版本。
 */
class ReleaseApiTest {

    private static final String TENANT = "t1";
    private static final String TIMESTAMP = "2026-02-01T00:00:00Z";

    private final InMemoryMetadataStore metadataStore = new InMemoryMetadataStore();
    private InMemorySnapshotStore snapshotStore;
    private MockMvc mvc;
    private Session session;
    private String appId;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_release_api_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        DataAccessFacade dataAccess = new DataAccessFacade(dataSource);

        InMemoryIdentityStore identityStore = new InMemoryIdentityStore(
                null,
                List.of(new OrgMember(
                        "m-1", TENANT, "alice", "Alice", null, null, null,
                        MemberStatus.ACTIVE, Map.of(), "system", TIMESTAMP,
                        "system", TIMESTAMP, false, 1)),
                List.of(), List.of());
        snapshotStore = new InMemorySnapshotStore();
        SessionPermissionService permissionService = new SessionPermissionService(
                snapshotStore, metadataStore, new PermissionContextFactory(identityStore));
        DaosPermissionChecker permissionChecker = new DaosPermissionChecker(permissionService);
        SchemaEvolutionService evolutionService = new SchemaEvolutionService(
                metadataStore, snapshotStore, new InMemoryMigrationStore(),
                new DefaultMigrationCompiler(), dataAccess, new H2Dialect());

        mvc = MockMvcBuilders.standaloneSetup(
                        new SchemaEvolutionController(
                                metadataStore, evolutionService, permissionChecker),
                        new ReleaseController(
                                snapshotStore, evolutionService, permissionChecker))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        LoginUser loginUser = new LoginUser();
        loginUser.setUsername("alice");
        SecurityUtil.set(loginUser);

        Instant now = Instant.parse(TIMESTAMP);
        session = new Session(
                "s-1", TENANT, "acc-1", "alice", "m-1", "hash",
                now, now.plusSeconds(3600), null, null);

        appId = new AppService(metadataStore, identityStore, snapshotStore, Clock.systemUTC())
                .create(TENANT, "m-1", "alice", "demo", "演示应用", null, null)
                .id();
    }

    @AfterEach
    void tearDown() {
        SecurityUtil.clear();
    }

    @Test
    void publishWithRemarksThenRollbackToFirstRelease() throws Exception {
        saveDraft(1, List.of(field("f-1", "title")));

        mvc.perform(post("/api/apps/{appId}/schema/migrations", appId)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"第一版\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toVersion").value(1));

        saveDraft(2, List.of(field("f-1", "title"), field("f-2", "summary")));

        mvc.perform(post("/api/apps/{appId}/schema/migrations", appId)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"第二版\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toVersion").value(2));

        mvc.perform(get("/api/apps/{appId}/releases", appId)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].releaseVersion").value(2))
                .andExpect(jsonPath("$.data[0].remark").value("第二版"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[1].releaseVersion").value(1))
                .andExpect(jsonPath("$.data[1].remark").value("第一版"))
                .andExpect(jsonPath("$.data[1].active").value(false));

        mvc.perform(post("/api/apps/{appId}/releases/{releaseVersion}/rollback", appId, 1)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session))
                .andExpect(status().isOk());

        mvc.perform(get("/api/apps/{appId}/releases", appId)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].releaseVersion").value(2))
                .andExpect(jsonPath("$.data[0].active").value(false))
                .andExpect(jsonPath("$.data[1].releaseVersion").value(1))
                .andExpect(jsonPath("$.data[1].active").value(true));
    }

    @Test
    void applyAcceptsMissingRequestBody() throws Exception {
        saveDraft(1, List.of(field("f-1", "title")));

        mvc.perform(post("/api/apps/{appId}/schema/migrations", appId)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toVersion").value(1));

        mvc.perform(get("/api/apps/{appId}/releases", appId)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].remark").doesNotExist());
    }

    private void saveDraft(int schemaVersion, List<SchemaField> fields) {
        SchemaApp app = metadataStore.findDraft(appId).orElseThrow();
        SchemaTable table = new SchemaTable(
                "t1", appId, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields,
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        metadataStore.saveDraft(new SchemaApp(
                app.id(), app.code(), app.displayName(), app.description(), app.icon(),
                MetadataStatus.DRAFT, List.of(table), List.of(), app.permissions(), app.ext(),
                app.createdBy(), app.createdAt(), "system", TIMESTAMP, false, schemaVersion));
    }

    private SchemaField field(String id, String code) {
        return new SchemaField(
                id, appId, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
