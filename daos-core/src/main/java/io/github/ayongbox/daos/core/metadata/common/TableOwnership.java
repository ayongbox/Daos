package io.github.ayongbox.daos.core.metadata.common;

/**
 * 表归属。平台表全局唯一、只读引用；应用表由 ownerAppId 拥有，跨应用只能以只读投影共享。
 */
public enum TableOwnership {
    /** 平台系统表：用户、部门、字典、附件等，所有应用只读引用。 */
    PLATFORM,
    /** 应用业务表：归属单个应用，可被其他应用以 ExternalTableRef 只读引用。 */
    APPLICATION
}
