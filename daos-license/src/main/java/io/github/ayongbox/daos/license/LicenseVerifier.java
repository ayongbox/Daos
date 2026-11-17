package io.github.ayongbox.daos.license;

/**
 * 授权校验契约。签名验证失败或过期时返回 valid=false，由调用方降级。
 */
public interface LicenseVerifier {

    LicenseInfo verify(String licenseContent);
}
