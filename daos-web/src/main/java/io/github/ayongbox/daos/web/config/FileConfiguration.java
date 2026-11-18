package io.github.ayongbox.daos.web.config;

import io.github.ayongbox.baseon.core.spi.ObjectStorageService;
import io.github.ayongbox.daos.core.spi.FileStore;
import io.github.ayongbox.daos.runtime.file.FileDownloadTokens;
import io.github.ayongbox.daos.runtime.file.FileService;
import io.github.ayongbox.daos.runtime.file.LocalObjectStorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;

/**
 * 附件装配。默认本地存储；切换为对象存储时把 {@code daos.file.storage} 设为 minio，
 * 并引入 baseon-starter-oss-minio 配置端点与凭据。
 */
@Configuration
@EnableConfigurationProperties(DaosFileProperties.class)
public class FileConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FileConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "daos.file", name = "storage", havingValue = "local",
            matchIfMissing = true)
    public ObjectStorageService localObjectStorageService(DaosFileProperties properties) {
        return new LocalObjectStorageService(Path.of(properties.getLocalRoot()));
    }

    @Bean
    public FileDownloadTokens fileDownloadTokens(DaosFileProperties properties) {
        return new FileDownloadTokens(
                resolveSecret(properties.getDownloadSecret()),
                Clock.systemUTC(), properties.getSignedUrlTtl());
    }

    @Bean
    public FileService fileService(
            ObjectStorageService objectStorageService,
            FileStore fileStore,
            FileDownloadTokens fileDownloadTokens,
            DaosFileProperties properties) {
        return new FileService(
                objectStorageService, fileStore, fileDownloadTokens,
                properties.getMaxSize(), properties.getAllowedContentTypes(), Clock.systemUTC());
    }

    /**
     * 未配置签名密钥时生成进程内随机密钥：重启后旧链接失效，多实例需显式配置。
     */
    private static byte[] resolveSecret(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        log.warn("未配置 daos.file.download-secret，已生成临时密钥：重启后签名下载链接失效，"
                + "多实例部署必须显式配置");
        return secret;
    }
}
