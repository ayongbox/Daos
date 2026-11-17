package io.github.ayongbox.daos.core.migration;

/**
 * 迁移台账状态。一次迁移只有三种终态，执行中不落库。
 */
public enum MigrationStatus {
    APPLIED,
    ROLLED_BACK,
    FAILED
}
