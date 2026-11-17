package io.github.ayongbox.daos.core.file;

import java.util.Objects;

/**
 * 附件字段中的文件引用。只保留展示与下载所需信息，不暴露存储键。
 */
public record FileReference(String id, String fileName, String contentType, long size) {

    public FileReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(contentType, "contentType");
    }
}
