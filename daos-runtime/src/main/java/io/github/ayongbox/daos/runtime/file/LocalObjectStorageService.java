package io.github.ayongbox.daos.runtime.file;

import io.github.ayongbox.baseon.core.model.BizException;
import io.github.ayongbox.baseon.core.model.CommonErrorCode;
import io.github.ayongbox.baseon.core.spi.ObjectInfo;
import io.github.ayongbox.baseon.core.spi.ObjectStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

/**
 * 本地文件系统对象存储。单机部署默认使用，根目录由 {@code daos.file.local-root} 指定。
 *
 * <p>对象键按 {@code 桶/键} 映射到根目录下的路径，越界键直接拒绝。
 * 本地存储无法生成外部预签名地址，签名下载由 Daos 的
 * {@link FileDownloadTokens} 在应用层提供。</p>
 */
public final class LocalObjectStorageService implements ObjectStorageService {

    private static final String DEFAULT_BUCKET = "default";

    private final Path root;

    public LocalObjectStorageService(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    @Override
    public ObjectInfo upload(String bucket, String objectKey, InputStream in, String contentType) {
        Objects.requireNonNull(in, "in");
        Path target = resolve(bucket, objectKey);
        try {
            Files.createDirectories(target.getParent());
            long size = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return new ObjectInfo(bucketName(bucket), objectKey, size, null,
                    contentType == null ? "application/octet-stream" : contentType);
        } catch (IOException error) {
            throw new UncheckedIOException("本地文件写入失败: " + objectKey, error);
        }
    }

    @Override
    public InputStream download(String bucket, String objectKey) {
        Path target = resolve(bucket, objectKey);
        if (!Files.isRegularFile(target)) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "文件不存在: " + objectKey);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException error) {
            throw new UncheckedIOException("本地文件读取失败: " + objectKey, error);
        }
    }

    @Override
    public void delete(String bucket, String objectKey) {
        try {
            Files.deleteIfExists(resolve(bucket, objectKey));
        } catch (IOException error) {
            throw new UncheckedIOException("本地文件删除失败: " + objectKey, error);
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        return Files.isRegularFile(resolve(bucket, objectKey));
    }

    @Override
    public String presignedUrl(String bucket, String objectKey, Duration expiry) {
        throw new UnsupportedOperationException(
                "本地存储不支持预签名地址，请使用 Daos 文件接口生成签名下载链接");
    }

    private Path resolve(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("对象键不能为空");
        }
        Path target = root.resolve(bucketName(bucket)).resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法对象键: " + objectKey);
        }
        return target;
    }

    private static String bucketName(String bucket) {
        return bucket == null || bucket.isBlank() ? DEFAULT_BUCKET : bucket;
    }
}
