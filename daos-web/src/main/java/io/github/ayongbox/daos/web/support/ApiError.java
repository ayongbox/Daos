package io.github.ayongbox.daos.web.support;

/**
 * 统一错误响应体。
 */
public record ApiError(String error, String message) {
}
