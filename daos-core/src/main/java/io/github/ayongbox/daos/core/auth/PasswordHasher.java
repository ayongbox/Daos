package io.github.ayongbox.daos.core.auth;

/**
 * 密码哈希契约。实现必须使用带盐的慢哈希，且校验使用常量时间比较。
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String encoded);
}
