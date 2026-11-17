package io.github.ayongbox.daos.web.auth;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前请求已解析的权限上下文。数据接口在解析后写入，供 Baseon 权限检查与审计读取。
 *
 * <p>上下文与应用绑定：同一请求只服务一个应用路径，读取时校验 {@code appId}，
 * 避免把 A 应用的权限上下文复用到 B 应用。</p>
 */
public final class PermissionContextHolder {

    public static final String ATTRIBUTE = PermissionContextHolder.class.getName() + ".CONTEXT";

    private PermissionContextHolder() {
    }

    public static void put(HttpServletRequest request, String appId, PermissionContext context) {
        request.setAttribute(ATTRIBUTE, new Entry(appId, context));
    }

    public static PermissionContext get(HttpServletRequest request) {
        Entry entry = entry(request);
        return entry == null ? null : entry.context();
    }

    /**
     * 仅当已解析上下文属于指定应用时返回，否则返回 null 由调用方重新解析。
     */
    public static PermissionContext get(HttpServletRequest request, String appId) {
        Entry entry = entry(request);
        if (entry == null || appId == null || !appId.equals(entry.appId())) {
            return null;
        }
        return entry.context();
    }

    private static Entry entry(HttpServletRequest request) {
        return (Entry) request.getAttribute(ATTRIBUTE);
    }

    private record Entry(String appId, PermissionContext context) {
    }
}
