package io.github.ayongbox.daos.web.schema;

/**
 * 发布请求体。请求体可省略，{@code remark} 为可选的发布说明。
 */
public record PublishRequest(String remark) {
}
