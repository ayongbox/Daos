package io.github.ayongbox.daos.runtime.permission;

/**
 * 权限重写失败。权限模型无法安全编译为 SQL 条件时必须显式失败，禁止静默降级。
 */
public class PermissionRewriteException extends RuntimeException {

    public PermissionRewriteException(String message) {
        super(message);
    }

    public PermissionRewriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
