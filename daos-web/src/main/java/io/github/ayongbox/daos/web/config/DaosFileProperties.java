package io.github.ayongbox.daos.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 附件配置。{@code storage=local} 使用本地目录，{@code storage=minio} 交给
 * baseon-starter-oss-minio 装配对象存储。
 */
@ConfigurationProperties(prefix = "daos.file")
public class DaosFileProperties {

    private String storage = "local";
    private String localRoot = "./data/files";
    private long maxSize = 20L * 1024 * 1024;
    private List<String> allowedContentTypes = List.of(
            "image/*",
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/zip",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    private String downloadSecret = "";
    private Duration signedUrlTtl = Duration.ofMinutes(5);

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public String getDownloadSecret() {
        return downloadSecret;
    }

    public void setDownloadSecret(String downloadSecret) {
        this.downloadSecret = downloadSecret;
    }

    public Duration getSignedUrlTtl() {
        return signedUrlTtl;
    }

    public void setSignedUrlTtl(Duration signedUrlTtl) {
        this.signedUrlTtl = signedUrlTtl;
    }
}
