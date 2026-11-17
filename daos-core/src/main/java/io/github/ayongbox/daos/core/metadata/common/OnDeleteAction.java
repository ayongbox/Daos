package io.github.ayongbox.daos.core.metadata.common;

/**
 * 关联删除 / 更新行为。
 */
public enum OnDeleteAction {
    RESTRICT,
    CASCADE,
    SET_NULL
}
