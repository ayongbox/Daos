package io.github.ayongbox.daos.core.file;

import java.util.Objects;

/**
 * 带有效期的签名下载链接。{@code url} 是相对路径，由客户端拼接服务地址。
 */
public record FileDownloadLink(String url, String expiresAt) {

    public FileDownloadLink {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
