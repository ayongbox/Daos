package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.common.SubjectType;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.resource.PermissionPoint;
import io.github.ayongbox.daos.core.metadata.resource.ResourceActionCatalog;
import io.github.ayongbox.daos.runtime.identity.ContextPermissionChecker;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F00-05 越权矩阵骨架。
 *
 * <p>矩阵以「身份 × 资源 × 动作 → 期望结果」为唯一表达形式，逐格验证权限裁决器。
 * 当前覆盖规则裁决层（{@link ContextPermissionChecker}），后续 API 级越权用例可复用同一
 * 身份定义，在 {@link #expectations(Map)} 追加行即可，不需要另建测试骨架。</p>
 *
 * <p>矩阵包含两条自动生成的全量基线：owner 对全部已注册权限点必须放行，匿名身份对全部
 * 权限点必须拒绝。新增资源或动作时，这两条基线会立即失败，强制补充矩阵覆盖。</p>
 */
class AccessMatrixTest {

    private static final String APP = "crm";
    private static final String OTHER_APP = "hr";
    private static final String USER = "u-1";
    private static final String TIMESTAMP = "2025-01-01T00:00:00Z";

    /** 固定时钟，避免生效窗口用例随真实时间漂移。 */
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);

    @TestFactory
    Stream<DynamicTest> authorizationMatrix() {
        Map<String, Principal> principals = principals();
        return expectations(principals).stream().map(row -> DynamicTest.dynamicTest(row.label(), () -> {
            ContextPermissionChecker checker =
                    new ContextPermissionChecker(context(row.principal()), CLOCK);
            assertEquals(
                    row.expected(),
                    checker.isAllowed(USER, row.resource(), row.action()),
                    "越权矩阵不匹配: " + row.label());
        }));
    }

    @Test
    void matrixCoversEveryRegisteredPermissionPoint() {
        List<Expectation> rows = expectations(principals());
        List<String> uncovered = new ArrayList<>();
        for (PermissionPoint point : ResourceActionCatalog.points()) {
            boolean allow = rows.stream().anyMatch(row -> row.expected()
                    && row.resource().type() == point.resourceType()
                    && row.action() == point.action());
            boolean deny = rows.stream().anyMatch(row -> !row.expected()
                    && row.resource().type() == point.resourceType()
                    && row.action() == point.action());
            if (!allow || !deny) {
                uncovered.add(point.code() + (allow ? "" : " 缺放行") + (deny ? "" : " 缺拒绝"));
            }
        }
        assertTrue(uncovered.isEmpty(), "以下权限点未被越权矩阵覆盖: " + uncovered);
    }

    @Test
    void rejectsUnknownSubjectEvenWhenRulesMatch() {
        Principal owner = principals().get("owner");
        ContextPermissionChecker checker = new ContextPermissionChecker(context(owner), CLOCK);

        assertFalse(checker.isAllowed("u-intruder", table(APP), ActionType.READ));
    }

    @Test
    void explicitDenyWinsOverAllow() {
        Principal denied = principals().get("denied-delete");
        ContextPermissionChecker checker = new ContextPermissionChecker(context(denied), CLOCK);

        assertTrue(checker.isAllowed(USER, table(APP), ActionType.UPDATE));
        assertFalse(checker.isAllowed(USER, table(APP), ActionType.DELETE));
    }

    // ---------------------------------------------------------------- 身份定义

    /**
     * 身份与其权限规则。新增身份只需在这里加一条，并在 expectations 中补充期望格。
     */
    private static Map<String, Principal> principals() {
        Map<String, Principal> principals = new LinkedHashMap<>();
        principals.put("anonymous", new Principal("anonymous", List.of(), List.of()));

        List<SchemaPermission> viewer = new ArrayList<>();
        viewer.add(allow("viewer-app", "r-viewer", ref(ResourceType.APP), ActionType.VIEW));
        viewer.add(allow("viewer-table", "r-viewer", table(APP), ActionType.READ));
        viewer.add(allow("viewer-view", "r-viewer", ref(ResourceType.VIEW), ActionType.VIEW));
        principals.put("viewer", new Principal("viewer", List.of("r-viewer"), viewer));

        List<SchemaPermission> editor = new ArrayList<>(viewer);
        editor.add(allow("editor-table", "r-editor", table(APP),
                ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE));
        editor.add(allow("editor-form", "r-editor", ref(ResourceType.FORM), ActionType.SUBMIT));
        editor.add(allow("editor-file", "r-editor", ref(ResourceType.FILE),
                ActionType.UPLOAD, ActionType.DOWNLOAD));
        principals.put("editor", new Principal("editor", List.of("r-editor"), editor));

        principals.put("owner", new Principal("owner", List.of("r-owner"), ownerRules()));

        List<SchemaPermission> deniedDelete = new ArrayList<>();
        deniedDelete.add(allow("denied-table", "r-denied-delete", table(APP),
                ActionType.CREATE, ActionType.READ, ActionType.UPDATE, ActionType.DELETE));
        deniedDelete.add(deny("denied-delete", "r-denied-delete", table(APP), ActionType.DELETE));
        principals.put("denied-delete",
                new Principal("denied-delete", List.of("r-denied-delete"), deniedDelete));

        principals.put("hr-viewer", new Principal("hr-viewer", List.of("r-hr-viewer"),
                List.of(allow("hr-viewer-table", "r-hr-viewer", table(OTHER_APP),
                        ActionType.READ))));

        principals.put("expired", new Principal("expired", List.of("r-expired"),
                List.of(rule("expired-table", SubjectType.ROLE, "r-expired", table(APP),
                        EffectType.ALLOW, null, "2026-01-01T00:00:00Z", ActionType.READ))));

        principals.put("public-reader", new Principal("public-reader", List.of(),
                List.of(rule("public-table", SubjectType.PUBLIC, null, table(APP),
                        EffectType.ALLOW, null, null, ActionType.READ))));

        return principals;
    }

    /** owner 对每个已注册权限点都有一条放行规则，用于锁定全量放行基线。 */
    private static List<SchemaPermission> ownerRules() {
        List<SchemaPermission> rules = new ArrayList<>();
        for (PermissionPoint point : ResourceActionCatalog.points()) {
            rules.add(allow(
                    "owner-" + point.code().replace(':', '-'),
                    "r-owner",
                    ref(point.resourceType()),
                    point.action()));
        }
        return rules;
    }

    // ---------------------------------------------------------------- 期望矩阵

    private static List<Expectation> expectations(Map<String, Principal> principals) {
        List<Expectation> rows = new ArrayList<>();

        // 全量基线：owner 全部放行，匿名身份全部拒绝。
        for (PermissionPoint point : ResourceActionCatalog.points()) {
            rows.add(expect(principals, "owner", ref(point.resourceType()), point.action(), true));
            rows.add(expect(principals, "anonymous", ref(point.resourceType()), point.action(), false));
        }

        // viewer：只读。
        rows.add(expect(principals, "viewer", ref(ResourceType.APP), ActionType.VIEW, true));
        rows.add(expect(principals, "viewer", ref(ResourceType.VIEW), ActionType.VIEW, true));
        rows.add(expect(principals, "viewer", table(APP), ActionType.READ, true));
        rows.add(expect(principals, "viewer", table(APP), ActionType.CREATE, false));
        rows.add(expect(principals, "viewer", table(APP), ActionType.UPDATE, false));
        rows.add(expect(principals, "viewer", table(APP), ActionType.DELETE, false));
        rows.add(expect(principals, "viewer", ref(ResourceType.APP), ActionType.DESIGN, false));
        rows.add(expect(principals, "viewer", ref(ResourceType.APP), ActionType.PUBLISH, false));
        rows.add(expect(principals, "viewer", ref(ResourceType.APP), ActionType.MANAGE_MEMBER, false));
        rows.add(expect(principals, "viewer", ref(ResourceType.FORM), ActionType.SUBMIT, false));
        rows.add(expect(principals, "viewer", ref(ResourceType.FILE), ActionType.DOWNLOAD, false));
        rows.add(expect(principals, "viewer", ref(ResourceType.FLOW), ActionType.APPROVE, false));
        rows.add(expect(principals, "viewer", ref(ResourceType.FIELD), ActionType.WRITE, false));

        // editor：可写数据，不可管理应用。
        rows.add(expect(principals, "editor", table(APP), ActionType.CREATE, true));
        rows.add(expect(principals, "editor", table(APP), ActionType.UPDATE, true));
        rows.add(expect(principals, "editor", table(APP), ActionType.DELETE, true));
        rows.add(expect(principals, "editor", ref(ResourceType.FORM), ActionType.SUBMIT, true));
        rows.add(expect(principals, "editor", ref(ResourceType.FILE), ActionType.UPLOAD, true));
        rows.add(expect(principals, "editor", ref(ResourceType.FILE), ActionType.DOWNLOAD, true));
        rows.add(expect(principals, "editor", ref(ResourceType.FILE), ActionType.DELETE, false));
        rows.add(expect(principals, "editor", ref(ResourceType.APP), ActionType.PUBLISH, false));
        rows.add(expect(principals, "editor", ref(ResourceType.APP), ActionType.DESIGN, false));
        rows.add(expect(principals, "editor", ref(ResourceType.FIELD), ActionType.WRITE, false));

        // 显式拒绝优先于放行。
        rows.add(expect(principals, "denied-delete", table(APP), ActionType.READ, true));
        rows.add(expect(principals, "denied-delete", table(APP), ActionType.DELETE, false));

        // 跨应用不串权。
        rows.add(expect(principals, "hr-viewer", table(APP), ActionType.READ, false));
        rows.add(expect(principals, "hr-viewer", table(OTHER_APP), ActionType.READ, true));

        // 生效窗口。
        rows.add(expect(principals, "expired", table(APP), ActionType.READ, false));
        rows.add(expect(principals, "public-reader", table(APP), ActionType.READ, true));

        return rows;
    }

    // ---------------------------------------------------------------- 构造工具

    private static PermissionContext context(Principal principal) {
        return new PermissionContext(
                "tenant-1", USER, principal.roleIds(), List.of("dept-1"), principal.rules());
    }

    private static ResourceRef table(String appCode) {
        return new ResourceRef(ResourceType.TABLE, appCode, null, List.of());
    }

    /** FIELD 规则必须带字段集合，其余资源用空 objectCode 表示应用内全部对象。 */
    private static ResourceRef ref(ResourceType type) {
        return type == ResourceType.FIELD
                ? new ResourceRef(type, APP, "contacts", List.of("salary"))
                : new ResourceRef(type, APP, null, List.of());
    }

    private static SchemaPermission allow(
            String code, String roleId, ResourceRef resource, ActionType... actions) {
        return rule(code, SubjectType.ROLE, roleId, resource,
                EffectType.ALLOW, null, null, actions);
    }

    private static SchemaPermission deny(
            String code, String roleId, ResourceRef resource, ActionType... actions) {
        return rule(code, SubjectType.ROLE, roleId, resource,
                EffectType.DENY, null, null, actions);
    }

    private static SchemaPermission rule(
            String code,
            SubjectType subjectType,
            String subjectId,
            ResourceRef resource,
            EffectType effect,
            String effectiveFrom,
            String effectiveTo,
            ActionType... actions) {
        return new SchemaPermission(
                "p-" + code, "app-crm", code, code, null,
                new PermissionSubject(subjectType, subjectId, false),
                resource, List.of(actions), effect,
                null, null, FieldMask.empty(), 100, true,
                effectiveFrom, effectiveTo, false, null,
                "test", TIMESTAMP, "test", TIMESTAMP, false, 1);
    }

    private static Expectation expect(
            Map<String, Principal> principals,
            String principal,
            ResourceRef resource,
            ActionType action,
            boolean expected) {
        return new Expectation(principals.get(principal), resource, action, expected);
    }

    private record Principal(String name, List<String> roleIds, List<SchemaPermission> rules) {
    }

    private record Expectation(
            Principal principal, ResourceRef resource, ActionType action, boolean expected) {

        String label() {
            return principal.name() + " " + resource.encode() + " " + action.code()
                    + " => " + (expected ? "ALLOW" : "DENY");
        }
    }
}
