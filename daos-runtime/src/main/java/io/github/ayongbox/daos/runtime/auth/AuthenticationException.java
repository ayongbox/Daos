package io.github.ayongbox.daos.runtime.auth;

/**
 * 登录或会话校验失败。对外统一暴露同一类错误，避免枚举账号。
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
}
