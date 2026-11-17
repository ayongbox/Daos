package io.github.ayongbox.daos.license;

/**
 * 受控能力项。未授权时按能力项降级，而不是整体不可用。
 */
public enum LicenseFeature {
    MULTI_TENANT,
    CLUSTER,
    LDAP_AD,
    DINGTALK,
    WECOM,
    SCIM,
    AUDIT_LONG_TERM,
    AUDIT_TAMPER_PROOF,
    COMPONENT_PRIVATE_REGISTRY,
    COMPONENT_MARKET,
    FULL_CONNECTOR,
    OFFLINE_UPGRADE
}
