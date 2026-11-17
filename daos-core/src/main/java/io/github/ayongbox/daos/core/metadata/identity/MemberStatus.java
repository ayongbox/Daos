package io.github.ayongbox.daos.core.metadata.identity;

/**
 * 成员状态。只有 {@link #ACTIVE} 成员可以构建权限上下文。
 */
public enum MemberStatus {
    ACTIVE,
    DISABLED
}
