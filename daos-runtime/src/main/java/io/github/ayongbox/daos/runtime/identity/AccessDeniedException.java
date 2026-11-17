package io.github.ayongbox.daos.runtime.identity;

/**
 * 身份解析失败。成员不存在、已停用或被删除时抛出，调用方应映射为 401/403。
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
