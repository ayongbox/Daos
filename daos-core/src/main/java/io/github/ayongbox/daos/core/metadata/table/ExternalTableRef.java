package io.github.ayongbox.daos.core.metadata.table;

import java.util.Objects;

/**
 * 跨应用只读引用。指向另一应用某张表的已发布快照，不复制数据、不产生物理表。
 *
 * <p>硬约束：只读、权限不穿透、版本不漂移。上游重新发布不会影响已有的引用。</p>
 */
public record ExternalTableRef(
        String appId,
        String appCode,
        String tableId,
        String tableCode,
        String refSnapshotId,
        long refReleaseVersion) {

    public ExternalTableRef {
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(appCode, "appCode");
        Objects.requireNonNull(tableId, "tableId");
        Objects.requireNonNull(tableCode, "tableCode");
        Objects.requireNonNull(refSnapshotId, "refSnapshotId");
        if (refSnapshotId.isBlank()) {
            throw new IllegalArgumentException("外部引用的快照标识不能为空");
        }
        if (refReleaseVersion < 1) {
            throw new IllegalArgumentException("外部引用的发布版本必须为正数");
        }
    }

    /**
     * 只读投影，恒为 true。保留方法是为了让约束在代码中显式可见。
     */
    public boolean readOnly() {
        return true;
    }
}
