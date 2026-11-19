package io.github.ayongbox.daos.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;
import io.github.ayongbox.daos.runtime.auth.AuthenticationService;
import io.github.ayongbox.daos.runtime.auth.InMemoryAccountStore;
import io.github.ayongbox.daos.runtime.auth.InMemorySessionStore;
import io.github.ayongbox.daos.runtime.auth.Pbkdf2PasswordHasher;
import io.github.ayongbox.daos.runtime.identity.IdentityAdminQueryService;
import io.github.ayongbox.daos.runtime.identity.IdentityAdminService;
import io.github.ayongbox.daos.runtime.identity.InMemoryIdentityStore;
import io.github.ayongbox.daos.web.admin.AdminDepartmentController;
import io.github.ayongbox.daos.web.admin.AdminMemberController;
import io.github.ayongbox.daos.web.auth.AuthController;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;
import io.github.ayongbox.daos.web.support.ApiExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台组织与成员接口的集成测试：部门增改删（含删除拒绝）、成员增改（含密码重置后可登录）。
 */
class AdminApiTest {

    private static final String TENANT = "t1";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(100_000);
        InMemoryAccountStore accountStore = new InMemoryAccountStore();
        accountStore.save(new Account(
                "acc-admin", TENANT, "root", "Root", hasher.hash("root-secret"), "m-admin", true));

        InMemoryIdentityStore identityStore = new InMemoryIdentityStore(null, null, null, null);
        AuthenticationService authenticationService = new AuthenticationService(
                accountStore, new InMemorySessionStore(), hasher,
                new AuditLog(new InMemoryAuditStore()));
        IdentityAdminService identityAdminService = new IdentityAdminService(
                identityStore, accountStore, hasher,
                new AuditLog(new InMemoryAuditStore()), Clock.systemUTC());
        IdentityAdminQueryService queryService = new IdentityAdminQueryService(identityStore);

        mvc = MockMvcBuilders.standaloneSetup(
                        new AdminDepartmentController(identityAdminService, queryService),
                        new AdminMemberController(identityAdminService, queryService),
                        new AuthController(authenticationService))
                .addInterceptors(new SessionAuthInterceptor(authenticationService, objectMapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void departmentCreateUpdateDeleteAndGuards() throws Exception {
        String token = login("root", "root-secret");

        JsonNode hq = createDepartment(token, "{\"code\":\"hq\",\"name\":\"总部\"}");
        String hqId = hq.get("id").asText();
        assertEquals("hq", hq.get("code").asText());
        assertEquals("/" + hqId + "/", hq.get("path").asText());
        assertTrue(hq.get("parentId").isNull());
        assertEquals(false, hq.get("deleted").asBoolean());

        JsonNode rd = createDepartment(token,
                "{\"parentId\":\"" + hqId + "\",\"code\":\"rd\",\"name\":\"研发\"}");
        String rdId = rd.get("id").asText();
        assertEquals("/" + hqId + "/" + rdId + "/", rd.get("path").asText());

        mvc.perform(get("/api/admin/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mvc.perform(put("/api/admin/departments/" + hqId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"总部（新）\",\"sortOrder\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("总部（新）"))
                .andExpect(jsonPath("$.data.sortOrder").value(5))
                .andExpect(jsonPath("$.data.code").value("hq"))
                .andExpect(jsonPath("$.data.path").value("/" + hqId + "/"));

        mvc.perform(delete("/api/admin/departments/" + hqId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.msg", containsString("子部门")));

        JsonNode fe = createDepartment(token,
                "{\"parentId\":\"" + rdId + "\",\"code\":\"fe\",\"name\":\"前端\"}");
        mvc.perform(delete("/api/admin/departments/" + rdId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.msg", containsString("子部门")));

        mvc.perform(delete("/api/admin/departments/" + fe.get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(post("/api/admin/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"displayName\":\"Bob\","
                                + "\"password\":\"bob-secret\",\"deptId\":\"" + rdId + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/departments/" + rdId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", containsString("成员")));

        // 空 parentId 移到根层级后，hq 无子部门即可软删除
        MvcResult moved = mvc.perform(put("/api/admin/departments/" + rdId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(data(moved).get("parentId").isNull());
        assertEquals("/" + rdId + "/", data(moved).get("path").asText());

        mvc.perform(delete("/api/admin/departments/" + hqId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/admin/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("rd"));
    }

    @Test
    void movingDepartmentRepathsSubtreeAndRejectsCycles() throws Exception {
        String token = login("root", "root-secret");

        String aId = createDepartment(token, "{\"code\":\"a\",\"name\":\"甲\"}").get("id").asText();
        String bId = createDepartment(token,
                "{\"parentId\":\"" + aId + "\",\"code\":\"b\",\"name\":\"乙\"}").get("id").asText();
        String cId = createDepartment(token,
                "{\"parentId\":\"" + bId + "\",\"code\":\"c\",\"name\":\"丙\"}").get("id").asText();
        String sibId = createDepartment(token, "{\"code\":\"sib\",\"name\":\"旁系\"}").get("id").asText();

        mvc.perform(put("/api/admin/departments/" + bId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + sibId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.path").value("/" + sibId + "/" + bId + "/"));

        JsonNode list = listDepartments(token);
        assertEquals("/" + sibId + "/" + bId + "/" + cId + "/", departmentPath(list, cId));

        mvc.perform(put("/api/admin/departments/" + bId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + cId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.msg", containsString("子部门")));

        mvc.perform(put("/api/admin/departments/" + bId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + bId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void memberCreateUpdateAndPasswordReset() throws Exception {
        String token = login("root", "root-secret");
        String rdId = createDepartment(token, "{\"code\":\"rd\",\"name\":\"研发\"}").get("id").asText();

        MvcResult created = mvc.perform(post("/api/admin/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"displayName\":\"Alice\","
                                + "\"password\":\"alice-123\",\"deptId\":\"" + rdId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.displayName").value("Alice"))
                .andExpect(jsonPath("$.data.deptId").value(rdId))
                .andExpect(jsonPath("$.data.deptName").value("研发"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn();
        String memberId = data(created).get("id").asText();

        // 创建的登录账号可以立即登录
        login("alice", "alice-123");

        mvc.perform(get("/api/admin/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].username").value("alice"));

        mvc.perform(post("/api/admin/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"displayName\":\"重复\","
                                + "\"password\":\"other-secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.msg", containsString("已存在")));

        MvcResult updated = mvc.perform(put("/api/admin/members/" + memberId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alice 2\",\"status\":\"DISABLED\",\"deptId\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Alice 2"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andReturn();
        assertTrue(data(updated).get("deptId").isNull());
        assertTrue(data(updated).get("deptName").isNull());

        // 重置密码后旧密码拒绝、新密码可登录
        mvc.perform(put("/api/admin/members/" + memberId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"new-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + TENANT
                                + "\",\"username\":\"alice\",\"password\":\"alice-123\"}"))
                .andExpect(status().isUnauthorized());
        login("alice", "new-secret");

        mvc.perform(put("/api/admin/members/" + memberId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    private JsonNode createDepartment(String token, String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/admin/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return data(result);
    }

    private JsonNode listDepartments(String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return data(result);
    }

    private String departmentPath(JsonNode list, String departmentId) {
        for (JsonNode department : list) {
            if (department.get("id").asText().equals(departmentId)) {
                return department.get("path").asText();
            }
        }
        throw new AssertionError("部门不存在: " + departmentId);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"" + TENANT + "\",\"username\":\"" + username
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return data(result).get("token").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
