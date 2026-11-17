package io.github.ayongbox.daos.license;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Ed25519 的授权验签实现。
 *
 * <p>授权串格式为 {@code base64url(payloadJson) + "." + base64url(signature)}。
 * 只持有公钥，私钥与签发工具不进入本模块。任何解析、验签或有效期异常都降级为
 * {@link LicenseInfo#invalid()}，由调用方按社区版能力继续运行。</p>
 */
public final class DefaultLicenseVerifier implements LicenseVerifier {

    private static final String ALGORITHM = "Ed25519";
    private static final String SEPARATOR = "\\.";

    private final PublicKey publicKey;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public DefaultLicenseVerifier(PublicKey publicKey) {
        this(publicKey, Clock.systemUTC());
    }

    public DefaultLicenseVerifier(PublicKey publicKey, Clock clock) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 从 X.509/SPKI 的 Base64 公钥文本构建验签器。
     */
    public static DefaultLicenseVerifier fromX509Base64(String publicKeyBase64) {
        Objects.requireNonNull(publicKeyBase64, "publicKeyBase64");
        try {
            byte[] encoded = Base64.getDecoder().decode(publicKeyBase64.trim());
            PublicKey key = KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(encoded));
            return new DefaultLicenseVerifier(key);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalArgumentException("授权公钥格式非法", error);
        }
    }

    @Override
    public LicenseInfo verify(String licenseContent) {
        if (licenseContent == null || licenseContent.isBlank()) {
            return LicenseInfo.invalid();
        }
        String[] parts = licenseContent.trim().split(SEPARATOR);
        if (parts.length != 2) {
            return LicenseInfo.invalid();
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (!verifySignature(payload, signature)) {
                return LicenseInfo.invalid();
            }
            LicensePayload decoded = objectMapper.readValue(payload, LicensePayload.class);
            return toInfo(decoded);
        } catch (IOException | IllegalArgumentException error) {
            return LicenseInfo.invalid();
        }
    }

    private boolean verifySignature(byte[] payload, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(payload);
            return verifier.verify(signature);
        } catch (GeneralSecurityException error) {
            return false;
        }
    }

    private LicenseInfo toInfo(LicensePayload payload) {
        if (payload.licensee() == null || payload.licensee().isBlank()) {
            return LicenseInfo.invalid();
        }

        boolean valid = notExpired(payload.expiresAt());
        return new LicenseInfo(
                payload.licensee(),
                payload.tier(),
                payload.features(),
                payload.issuedAt(),
                payload.expiresAt(),
                payload.maxTenants(),
                payload.maxNodes(),
                valid);
    }

    private boolean notExpired(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return true;
        }
        try {
            return !clock.instant().isAfter(Instant.parse(expiresAt));
        } catch (DateTimeParseException error) {
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LicensePayload(
            String licensee,
            LicenseTier tier,
            List<LicenseFeature> features,
            String issuedAt,
            String expiresAt,
            int maxTenants,
            int maxNodes) {
    }
}
