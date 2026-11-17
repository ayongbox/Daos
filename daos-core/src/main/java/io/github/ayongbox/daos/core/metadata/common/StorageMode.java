package io.github.ayongbox.daos.core.metadata.common;

/**
 * 表的存储模式，只在表级生效，不允许字段级覆盖。
 */
public enum StorageMode {
    /** 全部字段一对一映射为物理列，默认模式。 */
    PHYSICAL,
    /** 全部业务字段存入 _ext json 列，仅适用于字段稀疏的扩展表。 */
    JSON,
    /** 提升列走物理列，其余进 _ext json。提升列必须在 promotedColumns 中显式声明。 */
    HYBRID
}
