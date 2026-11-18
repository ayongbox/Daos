package io.github.ayongbox.daos.core.pipeline;

/**
 * 目标数据库方言。物理映射只允许出现在编译器实现中。
 */
public enum Dialect {
    MYSQL,
    POSTGRESQL,
    /** 仅用于本地开发与测试。 */
    H2
}
