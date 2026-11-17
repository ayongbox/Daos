package io.github.ayongbox.daos.core.metadata.common;

/**
 * 索引类型，按数据库方言由编译器降级。
 */
public enum IndexType {
    BTREE,
    HASH,
    FULLTEXT,
    GIN,
    GIST
}
