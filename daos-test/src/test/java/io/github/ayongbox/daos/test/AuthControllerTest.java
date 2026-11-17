package io.github.ayongbox.daos.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ayongbox.daos.core.metadata.identity.Account;
import io.github.ayongbox.daos.runtime.audit.AuditLog;
import io.github.ayongbox.daos.runtime.audit.InMemoryAuditStore;
import io.github.ayongbox.daos.runtime.auth.AuthenticationService;
import io.github.ayongbox.daos.runtime.auth.InMemoryAccountStore;
import io.github.ayongbox.daos.runtime.auth.InMemorySessionStore;
import io.github.ayongbox.daos.runtime.auth.Pbkdf2PasswordHasher;
import io.github.ayongbox.daos.web.auth.AuthController;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;
import io.github.ayongbox.daos.web.support.ApiExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private static final String TENANT = "t1";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(100_000);
        InMemoryAccountStore accountStore = new InMemoryAccountStore();
        accountStore.save(new Account("acc-1", TENANT, "alice", "Alice", hasher.hash("secret-123"), true));

        AuthenticationService service = new AuthenticationService(
                accountStore, new InMemorySessionStore(), hasher,
                new AuditLog(new InMemoryAuditStore()));

        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .addInterceptors(new SessionAuthInterceptor(service, objectMapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void loginThenReadCurrentSession() throws Exception {
        String token = login();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(TENANT))
                .andExpect(jsonPath("$.subjectId").value("acc-1"));
    }

    @Test
    void meWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t1\",\"username\":\"alice\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    void blankUsernameIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t1\",\"username\":\"\",\"password\":\"secret-123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void logoutRevokesSession() throws Exception {
        String token = login();

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"t1\",\"username\":\"alice\",\"password\":\"secret-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectId").value("acc-1"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
