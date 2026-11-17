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

    public boolean supports(LicenseFeature feature) {
        return valid && (tier == LicenseTier.ENTERPRISE || features.contains(feature));
    }
}
