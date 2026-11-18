package io.github.ayongbox.daos.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ayongbox.baseon.core.context.SecurityUtil;
import io.github.ayongbox.baseon.core.model.LoginUser;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.metadata.identity.MemberStatus;
import io.github.ayongbox.daos.core.metadata.identity.OrgMember;
import io.github.ayongbox.daos.runtime.app.AppService;
import io.github.ayongbox.daos.runtime.engine.SchemaEvolutionService;
import io.github.ayongbox.daos.runtime.engine.ddl.DefaultMigrationCompiler;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;
import io.github.ayongbox.daos.runtime.exec.DataAccessFacade;
import io.github.ayongbox.daos.runtime.identity.InMemoryIdentityStore;
import io.github.ayongbox.daos.runtime.identity.PermissionContextFactory;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemoryMigrationStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;
import io.github.ayongbox.daos.web.app.AppController;
import io.github.ayongbox.daos.web.auth.DaosPermissionChecker;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;
import io.github.ayongbox.daos.web.meta.MetadataCatalogController;
import io.github.ayongbox.daos.web.schema.SchemaEvolutionController;
import io.github.ayongbox.daos.web.support.ApiExceptionHandler;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppApiTest {

    private static final String TENANT = "t1";
    private static final String TIMESTAMP = "2026-02-01T00:00:00Z";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final InMemoryMetadataStore metadataStore = new InMemoryMetadataStore();
    private MockMvc mvc;
    private Session session;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_app_api;DB_CLOSE_DELAY=-1");
        DataAccessFacade dataAccess = new DataAccessFacade(dataSource);

        InMemoryIdentityStore identityStore = new InMemoryIdentityStore(
                null,
                List.of(new OrgMember(
                        "m-1", TENANT, "alice", "Alice", null, null, null,
                        MemberStatus.ACTIVE, Map.of(), "system", TIMESTAMP,
                        "system", TIMESTAMP, false, 1)),
                List.of(), List.of());
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        SessionPermissionService permissionService = new SessionPermissionService(
                snapshotStore, metadataStore, new PermissionContextFactory(identityStore));
        DaosPermissionChecker permissionChecker = new DaosPermissionChecker(permissionService);
        SchemaEvolutionService evolutionService = new SchemaEvolutionService(
                metadataStore, snapshotStore, new InMemoryMigrationStore(),
                new DefaultMigrationCompiler(), dataAccess, new PostgresDialect());

        mvc = MockMvcBuilders.standaloneSetup(
                        new AppController(
                                new AppService(metadataStore, identityStore, Clock.systemUTC()),
                                permissionChecker),
                        new SchemaEvolutionController(
                                metadataStore, evolutionService, permissionChecker),
                        new MetadataCatalogController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        LoginUser loginUser = new LoginUser();
        loginUser.setUsername("alice");
        SecurityUtil.set(loginUser);

        Instant now = Instant.parse(TIMESTAMP);
        session = new Session(
                "s-1", TENANT, "acc-1", "alice", "m-1", "hash",
                now, now.plusSeconds(3600), null, null);
    }

    @AfterEach
    void tearDown() {
        SecurityUtil.clear();
    }

    @Test
    void createsAppAndReadsDraftThroughOwnerPermission() throws Exception {
        String appId = createApp();

        mvc.perform(get("/api/apps/{appId}/schema/draft", appId)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("crm"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.permissions.length()").value(2));
    }

    @Test
    void exposesFieldTypeTemplates() throws Exception {
        mvc.perform(get("/api/meta/field-types")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(26))
                .andExpect(jsonPath("$.data[0].logicalType").value("text_short"))
                .andExpect(jsonPath("$.data[0].defaultConfig.configType").value("text_short"));
    }

    private String createApp() throws Exception {
        MvcResult result = mvc.perform(post("/api/apps")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"crm\",\"displayName\":\"客户管理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("crm"))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("id").asText();
    }
}
