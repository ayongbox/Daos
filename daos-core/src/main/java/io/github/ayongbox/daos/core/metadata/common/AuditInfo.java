package io.github.ayongbox.daos.core.metadata.common;

/**
 * 元数据审计字段契约，四类 schema 统一实现。
 */
public interface AuditInfo {

    String createdBy();

    String createdAt();

    String updatedBy();

    String updatedAt();

    boolean deleted();

    int schemaVersion();
}
