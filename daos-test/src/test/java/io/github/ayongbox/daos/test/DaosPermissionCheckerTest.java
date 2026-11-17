package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.common.SubjectType;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.runtime.identity.InMemoryIdentityStore;
import io.github.ayongbox.daos.runtime.identity.PermissionContextFactory;
import io.github.ayongbox.daos.runtime.identity.SessionPermissionService;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;
import io.github.ayongbox.daos.web.auth.DaosPermissionChecker;
import io.github.ayongbox.daos.web.auth.PermissionContextHolder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaosPermissionCheckerTest {

    private static final String APP_ID = "app-1";

    private final DaosPermissionChecker checker = new DaosPermissionChecker(
            new SessionPermissionService(
                    new InMemorySnapshotStore(),
                    new PermissionContextFactory(new InMemoryIdentityStore(null, null, null, null))));

    @BeforeEach
    void setUp() {
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void allowsWhenPublicRuleGrantsAction() {
        MockHttpServletRequest request = bindRequest();
        PermissionContextHolder.put(request, APP_ID, context(rule(ActionType.READ)));

        assertTrue(checker.hasPermission(APP_ID, "table:read"));
        assertFalse(checker.hasPermission(APP_ID, "table:delete"));
    }

    @Test
    void deniesWhenNoRuleMatches() {
        MockHttpServletRequest request = bindRequest();
        PermissionContextHolder.put(request, APP_ID, context());

        assertFalse(checker.hasPermission(APP_ID, "table:read"));
    }

    @Test
    void ignoresContextResolvedForAnotherApp() {
        MockHttpServletRequest request = bindRequest();
        PermissionContextHolder.put(request, "other-app", context(rule(ActionType.READ)));

        assertFalse(checker.hasPermission(APP_ID, "table:read"));
    }

    @Test
    void deniesWithoutRequestContext() {
        assertFalse(checker.hasPermission(APP_ID, "table:read"));
    }

    private static MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private static PermissionContext context(SchemaPermission... rules) {
        return new PermissionContext("t1", "m-1", List.of(), List.of(), List.of(rules));
    }

    private static SchemaPermission rule(ActionType action) {
        return new SchemaPermission(
                "p-1", APP_ID, "grant", "授权", null,
                new PermissionSubject(SubjectType.PUBLIC, null, true),
                new ResourceRef(ResourceType.TABLE, "demo", "users", List.of()),
                List.of(action), EffectType.ALLOW,
                null, null, null, 100, true, null, null, false,
                Map.of(), "system", null, "system", null, false, 1);
    }
}
