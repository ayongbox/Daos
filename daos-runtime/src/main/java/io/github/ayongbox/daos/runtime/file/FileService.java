package io.github.ayongbox.daos.runtime.file;

import io.github.ayongbox.baseon.core.spi.ObjectStorageService;
import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.file.FileDownloadLink;
import io.github.ayongbox.daos.core.file.FileObject;
import io.github.ayongbox.daos.core.spi.FileStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 附件服务：类型与大小校验、内容哈希、对象存储写入、归属校验与签名下载。
 *
 * <p>归属规则：文件按租户与应用隔离，跨租户或跨应用一律按不存在处理，
 * 避免通过文件 id 探测其他应用数据。</p>
 */
public final class FileService {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final ObjectStorageService storage;
    private final FileStore fileStore;
    private final FileDownloadTokens tokens;
    private final long maxSize;
    private final List<String> allowedContentTypes;
    private final Clock clock;

    public FileService(
            ObjectStorageService storage,
            FileStore fileStore,
            FileDownloadTokens tokens,
            long maxSize,
            List<String> allowedContentTypes,
            Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxSize <= 0) {
            throw new IllegalArgumentException("附件大小上限必须大于 0");
        }
        this.maxSize = maxSize;
        this.allowedContentTypes = List.copyOf(
                allowedContentTypes == null ? List.of("*/*") : allowedContentTypes);
    }

    /**
     * 上传文件。先按声明大小快速拒绝，再以流式方式统计真实大小并计算 SHA-256。
     */
    public FileObject upload(
            String tenantId,
            String appId,
            String originalName,
            String contentType,
            long declaredSize,
            InputStream in,
            String uploader) {
        requireScope(tenantId, appId);
        Objects.requireNonNull(in, "in");
        if (declaredSize > maxSize) {
            throw new IllegalArgumentException("文件大小超过限制: " + maxSize + " 字节");
        }
        String fileName = sanitizeName(originalName);
        String effectiveType = normalizeContentType(contentType);
        if (!allowed(effectiveType)) {
            throw new IllegalArgumentException("不允许的文件类型: " + effectiveType);
        }

        Path temp = null;
        try {
            temp = Files.createTempFile("daos-upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = copyBounded(in, temp, digest);

            String storageKey = storageKey(tenantId, appId, fileName);
            try (InputStream source = Files.newInputStream(temp)) {
                storage.upload(null, storageKey, source, effectiveType);
            }

            FileObject file = new FileObject(
                    Ids.ulid(), tenantId, appId, fileName, effectiveType, size,
                    storageKey, HexFormat.of().formatHex(digest.digest()),
                    uploader, clock.instant().toString());
            fileStore.save(file);
            return file;
        } catch (IOException error) {
            throw new UncheckedIOException("附件上传失败: " + fileName, error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 不可用", error);
        } finally {
            deleteQuietly(temp);
        }
    }

    public FileObject require(String tenantId, String appId, String fileId) {
        requireScope(tenantId, appId);
        return fileStore.findById(fileId)
                .filter(file -> file.tenantId().equals(tenantId) && file.appId().equals(appId))
                .orElseThrow(() -> new IllegalArgumentException("文件不存在: " + fileId));
    }

    public InputStream open(String tenantId, String appId, String fileId) {
        FileObject file = require(tenantId, appId, fileId);
        return storage.download(null, file.storageKey());
    }

    public FileDownloadLink signedLink(String tenantId, String appId, String fileId) {
        FileObject file = require(tenantId, appId, fileId);
        String token = tokens.issue(file.tenantId(), file.appId(), file.id());
        FileDownloadTokens.Payload payload = tokens.verify(token);
        return new FileDownloadLink(
                "/api/files/download?token=" + token, payload.expiresAt().toString());
    }

    public void delete(String tenantId, String appId, String fileId) {
        FileObject file = require(tenantId, appId, fileId);
        storage.delete(null, file.storageKey());
        fileStore.delete(file.id());
    }

    private long copyBounded(InputStream in, Path target, MessageDigest digest) throws IOException {
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxSize) {
                    throw new IllegalArgumentException(
                            "文件大小超过限制: " + maxSize + " 字节");
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private String storageKey(String tenantId, String appId, String fileName) {
        LocalDate date = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return tenantId + "/" + appId + "/"
                + date.getYear() + "/" + String.format("%02d", date.getMonthValue())
                + "/" + Ids.ulid() + extension(fileName);
    }

    private boolean allowed(String contentType) {
        for (String rule : allowedContentTypes) {
            if ("*/*".equals(rule) || rule.equalsIgnoreCase(contentType)) {
                return true;
            }
            if (rule.endsWith("/*")
                    && contentType.regionMatches(true, 0, rule.substring(0, rule.length() - 1), 0,
                    rule.length() - 1)) {
                return true;
            }
            if (rule.endsWith("*")
                    && contentType.regionMatches(true, 0, rule.substring(0, rule.length() - 1), 0,
                    rule.length() - 1)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        String value = contentType.trim();
        int separator = value.indexOf(';');
        return separator < 0 ? value : value.substring(0, separator).trim().toLowerCase();
    }

    private static String sanitizeName(String originalName) {
        String name = originalName == null ? "" : originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\\t]", "").trim();
        if (name.isEmpty()) {
            name = "file";
        }
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("文件名过长，最多 " + MAX_FILE_NAME_LENGTH + " 个字符");
        }
        return name;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot);
        return extension.matches("\\.[A-Za-z0-9]{1,10}") ? extension.toLowerCase() : "";
    }

    private static void requireScope(String tenantId, String appId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("租户不能为空");
        }
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("应用不能为空");
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件清理失败不影响上传结果
        }
    }

    /**
     * 暴露允许的类型清单，供管理端展示。
     */
    public Set<String> allowedContentTypes() {
        return Set.copyOf(allowedContentTypes);
    }
}
