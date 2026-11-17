package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.MaskType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.common.SubjectType;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.MaskRule;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.runtime.identity.ContextPermissionChecker;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextPermissionCheckerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String APP = "app1";

    @Test
    void allowsMatchingUserRule() {
        ContextPermissionChecker checker = checker(rule(
                "p1", subject(SubjectType.USER, "u1"), table("t_order"),
                List.of(ActionType.READ), EffectType.ALLOW, FieldMask.empty(), 100, null, null));

        assertTrue(checker.isAllowed("u1", table("t_order"), ActionType.READ));
        assertFalse(checker.isAllowed("u1", table("t_order"), ActionType.DELETE));
        assertFalse(checker.isAllowed("u2", table("t_order"), ActionType.READ));
    }

    @Test
    void denyOverridesAllow() {
        ContextPermissionChecker checker = checker(
                rule("p1", subject(SubjectType.USER, "u1"), table("t_order"),
                        List.of(ActionType.READ), EffectType.ALLOW, FieldMask.empty(), 100, null, null),
                rule("p2", subject(SubjectType.USER, "u1"), table("t_order"),
                        List.of(ActionType.READ), EffectType.DENY, FieldMask.empty(), 90, null, null));

        assertFalse(checker.isAllowed("u1", table("t_order"), ActionType.READ));
    }

    @Test
    void defaultsToDenyWithoutAllowRule() {
        ContextPermissionChecker checker = checker();

        assertFalse(checker.isAllowed("u1", table("t_order"), ActionType.READ));
    }

    @Test
    void matchesRoleAndDepartmentSubjects() {
        ContextPermissionChecker checker = checker(
                rule("p1", subject(SubjectType.ROLE, "r1"), table("t_order"),
                        List.of(ActionType.READ), EffectType.ALLOW, FieldMask.empty(), 100, null, null),
                rule("p2", subject(SubjectType.DEPARTMENT, "d1"), table("t_order"),
                        List.of(ActionType.UPDATE), EffectType.ALLOW, FieldMask.empty(), 100, null, null));

        assertTrue(checker.isAllowed("u1", table("t_order"), ActionType.READ));
        assertTrue(checker.isAllowed("u1", table("t_order"), ActionType.UPDATE));
    }

    @Test
    void ignoresRuleOutsideEffectiveWindow() {
        ContextPermissionChecker checker = checker(
                rule("expired", subject(SubjectType.USER, "u1"), table("t_order"),
                        List.of(ActionType.READ), EffectType.ALLOW, FieldMask.empty(),
                        100, null, "2025-12-31T00:00:00Z"),
                rule("future", subject(SubjectType.USER, "u1"), table("t_order"),
                        List.of(ActionType.UPDATE), EffectType.ALLOW, FieldMask.empty(),
                        100, "2026-02-01T00:00:00Z", null));

        assertFalse(checker.isAllowed("u1", table("t_order"), ActionType.READ));
        assertFalse(checker.isAllowed("u1", table("t_order"), ActionType.UPDATE));
    }

    @Test
    void publicRuleAppliesToCurrentSubject() {
        ContextPermissionChecker checker = checker(rule(
                "p1", subject(SubjectType.PUBLIC, null), table("t_order"),
                List.of(ActionType.READ), EffectType.ALLOW, FieldMask.empty(), 100, null, null));

        assertTrue(checker.isAllowed("u1", table("t_order"), ActionType.READ));
    }

    @Test
    void unknownOrgDenyFailsClosed() {
        ContextPermissionChecker checker = checker(
                rule("p1", subject(SubjectType.USER, "u1"), table("t_order"),
                        List.of(ActionType.READ), EffectType.ALLOW, FieldMask.empty(), 100, null, null),
                rule("p2", subject(SubjectType.ORG, "org1"), table("t_order"),
                        List.of(ActionType.READ), EffectType.DENY, FieldMask.empty(), 100, null, null));

        assertFalse(checker.isAllowed("u1", table("t_order"), ActionType.READ));
    }

    @Test
    void ignoresRuleForAnotherResource() {
        ContextPermissionChecker checker = checker(rule(
                "p1", subject(SubjectType.USER, "u1"), table("t_user"),
                List.of(ActionType.READ), EffectType.ALLOW, FieldMask.empty(), 100, null, null));

        assertFalse(checker.isAllowed("u1", table("t_order"), ActionType.READ));
    }

    @Test
    void fieldResourceRequiresFieldCoverage() {
        ContextPermissionChecker checker = checker(rule(
                "p1", subject(SubjectType.USER, "u1"), field("t_order", "f1", "f2"),
                List.of(ActionType.READ), EffectType.ALLOW, FieldMask.empty(), 100, null, null));

        assertTrue(checker.isAllowed("u1", field("t_order", "f1"), ActionType.READ));
        assertFalse(checker.isAllowed("u1", field("t_order", "f3"), ActionType.READ));
    }

    @Test
    void mergesFieldMaskByRulePriority() {
        FieldMask low = new FieldMask(
                List.of("f1"), List.of("f2"), List.of(),
                List.of(new MaskRule("f_phone", MaskType.EMAIL, null, null)));
        FieldMask high = new FieldMask(
                List.of("f3"), List.of(), List.of(),
                List.of(new MaskRule("f_phone", MaskType.PHONE, null, null)));

        ContextPermissionChecker checker = checker(
                rule("p_low", subject(SubjectType.USER, "u1"), table("t_order"),
                        List.of(ActionType.READ), EffectType.ALLOW, low, 10, null, null),
                rule("p_high", subject(SubjectType.USER, "u1"), table("t_order"),
                        List.of(ActionType.READ), EffectType.ALLOW, high, 100, null, null));

        FieldMask merged = checker.resolveFieldMask("u1", table("t_order"));

        assertEquals(Set.of("f1", "f3"), Set.copyOf(merged.hiddenFieldIds()));
        assertEquals(List.of("f2"), merged.readonlyFieldIds());
        assertEquals(1, merged.maskRules().size());
        assertEquals(MaskType.PHONE, merged.maskRules().get(0).type());
    }

    private static ContextPermissionChecker checker(SchemaPermission... rules) {
        PermissionContext context = new PermissionContext(
                "t1", "u1", List.of("r1"), List.of("d1"), List.of(rules));
        return new ContextPermissionChecker(context, CLOCK);
    }

    private static SchemaPermission rule(
            String id,
            PermissionSubject subject,
            ResourceRef resource,
            List<ActionType> actions,
            EffectType effect,
            FieldMask mask,
            int priority,
            String effectiveFrom,
            String effectiveTo) {
        return new SchemaPermission(
                id, APP, id, id, null, subject, resource, actions, effect,
                null, null, mask, priority, true, effectiveFrom, effectiveTo, false,
                Map.of(), null, null, null, null, false, 1);
    }

    private static PermissionSubject subject(SubjectType type, String id) {
        return new PermissionSubject(type, id, false);
    }

    private static ResourceRef table(String code) {
        return new ResourceRef(ResourceType.TABLE, APP, code, List.of());
    }

    private static ResourceRef field(String tableCode, String... fields) {
        return new ResourceRef(ResourceType.FIELD, APP, tableCode, List.of(fields));
    }
}
