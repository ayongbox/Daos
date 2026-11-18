package io.github.ayongbox.daos.runtime.permission;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.MaskRule;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 权限规则匹配的共享逻辑。查询重写与权限裁决必须使用同一套主体、资源与生效窗口判断，
 * 避免两处语义漂移。
 */
public final class PermissionRuleMatcher {

    private PermissionRuleMatcher() {
    }

    public enum SubjectMatch {
        MATCH,
        NO_MATCH,
        UNKNOWN
    }

    /**
     * 规则是否处于启用且生效的时间窗口内。时间格式非法时显式失败，不静默跳过。
     */
    public static boolean active(SchemaPermission rule, Instant now) {
        if (rule.effectiveFrom() != null && !rule.effectiveFrom().isBlank()
                && now.isBefore(parseInstant(rule, rule.effectiveFrom(), "effectiveFrom"))) {
            return false;
        }
        return rule.effectiveTo() == null || rule.effectiveTo().isBlank()
                || !now.isAfter(parseInstant(rule, rule.effectiveTo(), "effectiveTo"));
    }

    public static SubjectMatch matchSubject(PermissionSubject subject, PermissionContext permissions) {
        return switch (subject.type()) {
            case PUBLIC -> SubjectMatch.MATCH;
            case USER -> subject.id().equals(permissions.subjectId())
                    ? SubjectMatch.MATCH : SubjectMatch.NO_MATCH;
            case ROLE -> permissions.roleIds().contains(subject.id())
                    ? SubjectMatch.MATCH : SubjectMatch.NO_MATCH;
            case DEPARTMENT -> permissions.deptIds().contains(subject.id())
                    ? SubjectMatch.MATCH : SubjectMatch.NO_MATCH;
            case ORG -> SubjectMatch.UNKNOWN;
        };
    }

    /**
     * 资源匹配只认同一资源类型。FIELD 规则要求目标字段被规则字段集合覆盖，
     * 空字段集合表示该资源下全部字段。
     */
    public static boolean matchesResource(ResourceRef rule, ResourceRef target) {
        if (rule.type() != target.type()) {
            return false;
        }
        if (!rule.appCode().equals(target.appCode())) {
            return false;
        }
        // 表规则省略 objectCode 表示应用内全部表，与运行时数据访问的匹配语义一致。
        boolean wildcardTable = rule.type() == ResourceType.TABLE
                && (rule.objectCode() == null || rule.objectCode().isBlank());
        if (!wildcardTable && !Objects.equals(rule.objectCode(), target.objectCode())) {
            return false;
        }
        if (rule.type() != ResourceType.FIELD) {
            return true;
        }
        return rule.fieldCodes().isEmpty() || rule.fieldCodes().containsAll(target.fieldCodes());
    }

    /**
     * 多条 ALLOW 规则的字段掩码取并集，脱敏规则按优先级取第一条。
     */
    public static FieldMask mergeFieldMask(List<SchemaPermission> allows) {
        Set<String> hidden = new LinkedHashSet<>();
        Set<String> readonly = new LinkedHashSet<>();
        Set<String> required = new LinkedHashSet<>();
        Map<String, MaskRule> masks = new LinkedHashMap<>();

        List<SchemaPermission> ordered = allows.stream()
                .sorted(Comparator.comparingInt(SchemaPermission::priority).reversed()
                        .thenComparing(SchemaPermission::code))
                .toList();
        for (SchemaPermission rule : ordered) {
            FieldMask mask = rule.fieldMask();
            hidden.addAll(mask.hiddenFieldIds());
            readonly.addAll(mask.readonlyFieldIds());
            required.addAll(mask.requiredFieldIds());
            for (MaskRule maskRule : mask.maskRules()) {
                masks.putIfAbsent(maskRule.fieldId(), maskRule);
            }
        }
        return new FieldMask(
                List.copyOf(hidden), List.copyOf(readonly), List.copyOf(required), List.copyOf(masks.values()));
    }

    private static Instant parseInstant(SchemaPermission rule, String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException error) {
            throw new PermissionRewriteException(
                    "权限 " + rule.code() + " 的 " + field + " 不是合法 ISO-8601 时间: " + value, error);
        }
    }
}
