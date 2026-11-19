package io.github.ayongbox.daos.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.pipeline.Dialect;
import io.github.ayongbox.daos.runtime.dictionary.DictionaryAdminService;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcDictionaryStore;
import io.github.ayongbox.daos.runtime.store.jdbc.JdbcStoreSchema;
import io.github.ayongbox.daos.runtime.store.jdbc.JsonCodec;
import io.github.ayongbox.daos.web.admin.DictionaryAdminController;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;
import io.github.ayongbox.daos.web.support.ApiExceptionHandler;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台字典接口的集成测试：覆盖写入、查询、校验与软删除，仓储落到 JDBC（daos_dictionary）。
 */
class DictionaryApiTest {

    private static final String TENANT = "t1";
    private static final String APP = "app-1";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private JsonCodec codec;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:daos_dictionary_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        new JdbcStoreSchema(dataSource, Dialect.H2);
        jdbc = new JdbcTemplate(dataSource);
        codec = new JsonCodec();
        DictionaryAdminService service = new DictionaryAdminService(
                new JdbcDictionaryStore(jdbc, codec), Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new DictionaryAdminController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void upsertQueryAndSoftDeleteDictionary() throws Exception {
        MvcResult created = mvc.perform(put("/api/apps/{appId}/dictionaries/{code}", APP, "level")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"级别","description":"优先级","tree":false,"enabled":true,"items":[
                                  {"code":"high","label":"高","value":"1","color":"#ff0000","icon":"up",
                                   "sortOrder":1,"enabled":true,"defaultSelected":true,"description":"最紧急"},
                                  {"code":"low","label":"低","value":"2","sortOrder":2,"enabled":true}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("level"))
                .andExpect(jsonPath("$.data.name").value("级别"))
                .andExpect(jsonPath("$.data.tree").value(false))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].code").value("high"))
                .andExpect(jsonPath("$.data.items[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].defaultSelected").value(true))
                .andExpect(jsonPath("$.data.items[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                .andReturn();
        String dictId = dictId(created);

        mvc.perform(get("/api/apps/{appId}/dictionaries", APP)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("level"))
                .andExpect(jsonPath("$.data[0].items.length()").value(2));

        // 同编码覆盖写入：字典 id 不变，items 整体替换
        mvc.perform(put("/api/apps/{appId}/dictionaries/{code}", APP, "level")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"级别 2","items":[
                                  {"code":"mid","label":"中","value":"3"},
                                  {"code":"low","label":"低","value":"2"}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(dictId))
                .andExpect(jsonPath("$.data.name").value("级别 2"))
                .andExpect(jsonPath("$.data.tree").value(false))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].code").value("mid"))
                .andExpect(jsonPath("$.data.items[0].enabled").value(true));

        // 软删除后查询隐藏，重复删除拒绝
        mvc.perform(delete("/api/apps/{appId}/dictionaries/{code}", APP, "level")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mvc.perform(get("/api/apps/{appId}/dictionaries", APP)
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(delete("/api/apps/{appId}/dictionaries/{code}", APP, "level")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.msg", containsString("字典不存在")));

        // 重新写入复活同一字典，且新仓储实例（模拟重启）仍能读到
        mvc.perform(put("/api/apps/{appId}/dictionaries/{code}", APP, "level")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"级别 3\",\"items\":[{\"code\":\"high\",\"label\":\"高\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(dictId));

        DictionaryAdminService restarted = new DictionaryAdminService(
                new JdbcDictionaryStore(jdbc, codec), Clock.systemUTC());
        assertEquals(1, restarted.list(APP).size());
        assertEquals(dictId, restarted.list(APP).get(0).id());
    }

    @Test
    void upsertRejectsInvalidPayload() throws Exception {
        mvc.perform(put("/api/apps/{appId}/dictionaries/{code}", APP, "level")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        mvc.perform(put("/api/apps/{appId}/dictionaries/{code}", APP, "level")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"级别","items":[
                                  {"code":"a","label":"甲"},
                                  {"code":"a","label":"乙"}
                                ]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.msg", containsString("重复")));

        // 平铺字典不允许 parentId
        mvc.perform(put("/api/apps/{appId}/dictionaries/{code}", APP, "level")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"级别","tree":false,"items":[
                                  {"id":"i1","code":"a","label":"甲"},
                                  {"id":"i2","code":"b","label":"乙","parentId":"i1"}
                                ]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void treeDictionaryKeepsParentLinks() throws Exception {
        mvc.perform(put("/api/apps/{appId}/dictionaries/{code}", APP, "org")
                        .requestAttr(SessionAuthInterceptor.SESSION_ATTRIBUTE, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"组织","tree":true,"items":[
                                  {"id":"i1","code":"root","label":"根"},
                                  {"id":"i2","code":"leaf","label":"叶","parentId":"i1"}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tree").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].parentId").value("i1"));
    }

    private Session adminSession() {
        Instant now = Instant.now();
        return new Session(
                "session-1", TENANT, "acc-admin", "root", "m-admin", "token-hash",
                now, now.plusSeconds(3600), null, null);
    }

    private String dictId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }
}
