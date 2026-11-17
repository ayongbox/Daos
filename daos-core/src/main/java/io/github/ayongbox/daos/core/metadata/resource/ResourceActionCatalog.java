package io.github.ayongbox.daos.core.metadata.resource;

import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 资源与动作矩阵。动作与资源解耦，但合法组合由本矩阵唯一约束。
 *
 * <p>矩阵是权限点注册表的唯一事实来源：元数据校验、鉴权引擎与配置界面都从这里取动作集合，
 * 不允许在别处硬编码「某资源可做哪些动作」。</p>
 */
public final class ResourceActionCatalog {

    private static final Map<ResourceType, Set<ActionType>> ACTIONS = buildActions();
    private static final List<PermissionPoint> POINTS = buildPoints();
    private static final Map<String, PermissionPoint> POINTS_BY_CODE = buildPointsByCode();

    private ResourceActionCatalog() {
    }

    /**
     * 某资源类型允许的全部动作，按枚举声明顺序返回。
     */
    public static Set<ActionType> actionsOf(ResourceType resourceType) {
        Objects.requireNonNull(resourceType, "resourceType");
        return ACTIONS.get(resourceType);
    }

    public static boolean supports(ResourceType resourceType, ActionType action) {
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(action, "action");
        return ACTIONS.get(resourceType).contains(action);
    }

    public static void require(ResourceType resourceType, ActionType action) {
        if (!supports(resourceType, action)) {
            throw new IllegalArgumentException(
                    "资源 " + resourceType + " 不支持动作 " + action.code()
                            + "，允许: " + ACTIONS.get(resourceType).stream().map(ActionType::code).toList());
        }
    }

    /**
     * 全部已注册权限点，顺序稳定，可直接用于接口与配置界面。
     */
    public static List<PermissionPoint> points() {
        return POINTS;
    }

    /**
     * 按稳定编码解析权限点，未知编码显式失败。
     */
    public static PermissionPoint requirePoint(String code) {
        Objects.requireNonNull(code, "code");
        PermissionPoint point = POINTS_BY_CODE.get(code);
        if (point == null) {
            throw new IllegalArgumentException("未知权限点: " + code);
        }
        return point;
    }

    private static Map<ResourceType, Set<ActionType>> buildActions() {
        Map<ResourceType, Set<ActionType>> actions = new EnumMap<>(ResourceType.class);
        actions.put(ResourceType.APP, EnumSet.of(
                ActionType.VIEW, ActionType.DESIGN, ActionType.PUBLISH,
                ActionType.EXPORT, ActionType.DELETE, ActionType.MANAGE_MEMBER));
        actions.put(ResourceType.TABLE, EnumSet.of(
                ActionType.CREATE, ActionType.READ, ActionType.UPDATE, ActionType.DELETE,
                ActionType.IMPORT, ActionType.EXPORT, ActionType.BULK_UPDATE, ActionType.SHARE));
        actions.put(ResourceType.FORM, EnumSet.of(ActionType.SUBMIT, ActionType.VIEW));
        actions.put(ResourceType.VIEW, EnumSet.of(ActionType.VIEW, ActionType.EXPORT, ActionType.SHARE));
        actions.put(ResourceType.FLOW, EnumSet.of(
                ActionType.START, ActionType.APPROVE, ActionType.REJECT, ActionType.WITHDRAW,
                ActionType.TRANSFER, ActionType.ADD_SIGN, ActionType.VIEW));
        actions.put(ResourceType.MENU, EnumSet.of(ActionType.VIEW));
        actions.put(ResourceType.FILE, EnumSet.of(
                ActionType.UPLOAD, ActionType.DOWNLOAD, ActionType.DELETE));
        actions.put(ResourceType.FIELD, EnumSet.of(ActionType.READ, ActionType.WRITE, ActionType.EXPORT));
        return Collections.unmodifiableMap(actions);
    }

    private static List<PermissionPoint> buildPoints() {
        List<PermissionPoint> points = new ArrayList<>();
        for (Map.Entry<ResourceType, Set<ActionType>> entry : ACTIONS.entrySet()) {
            for (ActionType action : entry.getValue()) {
                points.add(new PermissionPoint(entry.getKey(), action));
            }
        }
        return List.copyOf(points);
    }

    private static Map<String, PermissionPoint> buildPointsByCode() {
        Map<String, PermissionPoint> byCode = new LinkedHashMap<>();
        for (PermissionPoint point : POINTS) {
            PermissionPoint previous = byCode.put(point.code(), point);
            if (previous != null) {
                throw new IllegalStateException("权限点编码重复: " + point.code());
            }
        }
        return Collections.unmodifiableMap(byCode);
    }
}
