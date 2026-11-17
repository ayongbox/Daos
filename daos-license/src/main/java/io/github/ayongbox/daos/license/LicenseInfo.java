package io.github.ayongbox.daos.license;

import java.util.List;

/**
 * 解析后的授权信息。
 */
public record LicenseInfo(
        String licensee,
        LicenseTier tier,
        List<LicenseFeature> features,
        String issuedAt,
        String expiresAt,
        int maxTenants,
        int maxNodes,
        boolean valid) {

    public LicenseInfo {
        tier = tier == null ? LicenseTier.COMMUNITY : tier;
        features = features == null ? List.of() : List.copyOf(features);
    }

    /**
     * 无法解析或验签失败时的安全默认值。
     */
    public static LicenseInfo invalid() {
        return new LicenseInfo("", LicenseTier.COMMUNITY, List.of(), null, null, 0, 0, false);
    }

    public boolean supports(LicenseFeature feature) {
        return valid && (tier == LicenseTier.ENTERPRISE || features.contains(feature));
    }
}
