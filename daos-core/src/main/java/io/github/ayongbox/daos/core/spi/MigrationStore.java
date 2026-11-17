package io.github.ayongbox.daos.core.spi;

import io.github.ayongbox.daos.core.migration.MigrationRecord;

import java.util.List;
import java.util.Optional;

/**
 * 迁移台账仓储。只追加与更新状态，不删除历史。
 */
public interface MigrationStore {

    void append(MigrationRecord record);

    void update(MigrationRecord record);

    Optional<MigrationRecord> findById(String migrationId);

    /**
     * 按创建时间倒序返回指定应用的迁移记录。
     */
    List<MigrationRecord> listByApp(String appId);
}
