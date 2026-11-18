package io.github.ayongbox.daos.runtime.store.jdbc;

import io.github.ayongbox.daos.core.file.FileObject;
import io.github.ayongbox.daos.core.spi.FileStore;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 文件元数据仓储的数据库实现。字节内容仍由对象存储负责。
 */
public final class JdbcFileStore implements FileStore {

    private final JdbcTemplate jdbc;
    private final JsonCodec codec;

    public JdbcFileStore(JdbcTemplate jdbc, JsonCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void save(FileObject file) {
        String payload = codec.write(file);
        int updated = jdbc.update(
                "UPDATE daos_file SET tenant_id = ?, app_id = ?, payload = ? WHERE file_id = ?",
                file.tenantId(), file.appId(), payload, file.id());
        if (updated == 0) {
            try {
                jdbc.update(
                        "INSERT INTO daos_file (file_id, tenant_id, app_id, payload)"
                                + " VALUES (?, ?, ?, ?)",
                        file.id(), file.tenantId(), file.appId(), payload);
            } catch (DuplicateKeyException e) {
                throw new IllegalStateException("文件元数据已存在: " + file.id(), e);
            }
        }
    }

    @Override
    public Optional<FileObject> findById(String fileId) {
        return payloads("SELECT payload FROM daos_file WHERE file_id = ?", fileId)
                .stream()
                .findFirst()
                .map(payload -> codec.read(payload, FileObject.class));
    }

    @Override
    public void delete(String fileId) {
        jdbc.update("DELETE FROM daos_file WHERE file_id = ?", fileId);
    }

    private List<String> payloads(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args);
    }
}
