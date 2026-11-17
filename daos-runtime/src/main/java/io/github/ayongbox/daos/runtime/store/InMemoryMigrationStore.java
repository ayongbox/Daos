package io.github.ayongbox.daos.runtime.store;

import io.github.ayongbox.daos.core.migration.MigrationRecord;
import io.github.ayongbox.daos.core.spi.MigrationStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 迁移台账内存实现。用于单机运行与测试，生产环境由持久化实现替换。
 */
public final class InMemoryMigrationStore implements MigrationStore {

    private final Map<String, MigrationRecord> records = new HashMap<>();

    @Override
    public synchronized void append(MigrationRecord record) {
        if (records.containsKey(record.id())) {
            throw new IllegalStateException("迁移记录已存在: " + record.id());
        }
        records.put(record.id(), record);
    }

    @Override
    public synchronized void update(MigrationRecord record) {
        if (!records.containsKey(record.id())) {
            throw new IllegalStateException("迁移记录不存在: " + record.id());
        }
        records.put(record.id(), record);
    }

    @Override
    public synchronized Optional<MigrationRecord> findById(String migrationId) {
        return Optional.ofNullable(records.get(migrationId));
    }

    @Override
    public synchronized List<MigrationRecord> listByApp(String appId) {
        List<MigrationRecord> result = new ArrayList<>();
        for (MigrationRecord record : records.values()) {
            if (record.appId().equals(appId)) {
                result.add(record);
            }
        }
        result.sort(Comparator.comparing(MigrationRecord::createdAt).reversed());
        return result;
    }
}
