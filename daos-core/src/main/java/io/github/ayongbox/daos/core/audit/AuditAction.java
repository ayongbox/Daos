package io.github.ayongbox.daos.core.audit;

/**
 * 审计动作。覆盖登录、数据访问与变更、发布和授权变更。
 */
public enum AuditAction {
    LOGIN,
    LOGOUT,
    READ,
    CREATE,
    UPDATE,
    DELETE,
    PUBLISH,
    GRANT,
    EXPORT
}
