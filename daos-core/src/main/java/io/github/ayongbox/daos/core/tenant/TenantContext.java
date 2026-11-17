package io.github.ayongbox.daos.core.tenant;

/**
 * 租户上下文契约。业务代码不手工解析租户，由过滤器或网关注入。
 */
public interface TenantContext {

    String tenantId();

    default String requireTenantId() {
        String tenantId = tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("租户上下文缺失");
        }
        return tenantId;
    }
}
