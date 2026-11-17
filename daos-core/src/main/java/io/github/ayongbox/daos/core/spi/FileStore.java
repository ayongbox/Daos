package io.github.ayongbox.daos.core.spi;

import io.github.ayongbox.daos.core.file.FileObject;

import java.util.Optional;

/**
 * 文件元数据仓储。字节内容由对象存储负责，本接口只保存归属与校验信息。
 */
public interface FileStore {

    void save(FileObject file);

    Optional<FileObject> findById(String fileId);

    void delete(String fileId);
}
