package io.github.ayongbox.daos.core.file;

import java.util.Objects;

/**
 * 文件元数据。字节内容存放在对象存储，本记录只描述归属、大小与校验信息。
 */
public record FileObject(
        String id,
        String tenantId,
        String appId,
        String fileName,
        String contentType,
        long size,
        String storageKey,
        String sha256,
        String uploader,
        String createdAt) {

    public FileObject {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(createdAt, "createdAt");
        if (size < 0) {
            throw new IllegalArgumentException("文件大小不能为负数");
        }
    }

    /**
     * 转为附件字段可存储的引用。附件字段值为本记录的 JSON 数组。
     */
    public FileReference reference() {
        return new FileReference(id, fileName, contentType, size);
    }
}
