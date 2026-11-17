package io.github.ayongbox.daos.web.auth;

import io.github.ayongbox.daos.core.ir.PermissionContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前请求已解析的权限上下文。数据接口在解析后写入，供 Baseon 权限检查与审计读取。
 */
public final class PermissionContextHolder {

    public static final String ATTRIBUTE = PermissionContextHolder.class.getName() + ".CONTEXT";

    private PermissionContextHolder() {
    }

    public static void put(HttpServletRequest request, PermissionContext context) {
        request.setAttribute(ATTRIBUTE, context);
    }

    public static PermissionContext get(HttpServletRequest request) {
        return (PermissionContext) request.getAttribute(ATTRIBUTE);
    }
}
