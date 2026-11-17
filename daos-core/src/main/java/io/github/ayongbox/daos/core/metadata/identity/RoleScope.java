package io.github.ayongbox.daos.core.metadata.identity;

/**
 * 角色作用域。平台角色对整个租户生效，应用角色只在所属应用内生效。
 */
public enum RoleScope {
    PLATFORM,
    APPLICATION
}
