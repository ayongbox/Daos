package io.github.ayongbox.daos.core.metadata.identity;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 本地账号。密码只保存哈希，任何层都不得持有明文。
 *
 * <p>账号按租户隔离，用户名在租户内唯一；停用后登录直接拒绝。
 * {@code memberId} 关联组织成员，权限上下文以成员 id 为主体；允许为空以支持尚未建档的账号。</p>
 */
public record Account(
        String id,
        String tenantId,
        String username,
        String displayName,
        String passwordHash,
        String memberId,
        boolean enabled) {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.@-]{3,64}");

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(passwordHash, "passwordHash");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("账号 " + username + " 的租户不能为空");
        }
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "用户名不合法: " + username + "，要求 [A-Za-z0-9_.@-]{3,64}");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("账号 " + username + " 的密码哈希不能为空");
        }
        if (memberId != null && memberId.isBlank()) {
            throw new IllegalArgumentException("账号 " + username + " 的成员 id 不能为空字符串");
        }
    }
}
