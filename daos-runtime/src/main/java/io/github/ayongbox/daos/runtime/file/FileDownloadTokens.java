package io.github.ayongbox.daos.runtime.file;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 文件下载签名。令牌绑定租户、应用与文件，过期即失效，篡改即拒绝。
 *
 * <p>令牌形如 {@code base64(tenant|app|file|expires).base64(hmac)}，
 * 由应用密钥签名，不依赖会话，用于浏览器直链与第三方回源。</p>
 */
public final class FileDownloadTokens {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final Clock clock;
    private final Duration ttl;

    public FileDownloadTokens(byte[] secret, Clock clock, Duration ttl) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length < 16) {
            throw new IllegalArgumentException("下载签名密钥至少 16 字节");
        }
        this.key = new SecretKeySpec(secret, HMAC_ALGORITHM);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("签名有效期必须大于 0");
        }
    }

    public String issue(String tenantId, String appId, String fileId) {
        long expiresAt = clock.instant().plus(ttl).getEpochSecond();
        String payload = tenantId + "|" + appId + "|" + fileId + "|" + expiresAt;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return encode(payloadBytes) + "." + encode(sign(payloadBytes));
    }

    public Payload verify(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("下载链接缺少令牌");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("下载链接无效");
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            signature = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("下载链接无效");
        }
        if (!MessageDigest.isEqual(sign(payloadBytes), signature)) {
            throw new IllegalArgumentException("下载链接签名不匹配");
        }
        String[] fields = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|", -1);
        if (fields.length != 4) {
            throw new IllegalArgumentException("下载链接无效");
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(fields[3]);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("下载链接无效");
        }
        if (clock.instant().getEpochSecond() > expiresAt) {
            throw new IllegalArgumentException("下载链接已过期");
        }
        return new Payload(fields[0], fields[1], fields[2],
                Instant.ofEpochSecond(expiresAt));
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException error) {
            throw new IllegalStateException("下载签名计算失败", error);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record Payload(String tenantId, String appId, String fileId, Instant expiresAt) {
    }
}
