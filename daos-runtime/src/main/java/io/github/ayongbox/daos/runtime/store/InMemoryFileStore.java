package io.github.ayongbox.daos.runtime.store;

import io.github.ayongbox.daos.core.file.FileObject;
import io.github.ayongbox.daos.core.spi.FileStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 文件元数据内存实现。生产环境由持久化实现替换，字节内容仍走对象存储。
 */
public final class InMemoryFileStore implements FileStore {

    private final Map<String, FileObject> files = new HashMap<>();

    @Override
    public synchronized void save(FileObject file) {
        files.put(file.id(), file);
    }

    @Override
    public synchronized Optional<FileObject> findById(String fileId) {
        return Optional.ofNullable(files.get(fileId));
    }

    @Override
    public synchronized void delete(String fileId) {
        files.remove(fileId);
    }
}
