package io.github.ayongbox.daos.web.auth;

/**
 * 登录请求。租户编码用于多租户隔离，缺省场景由调用方固定。
 */
public record LoginRequest(String tenantId, String username, String password) {
}
