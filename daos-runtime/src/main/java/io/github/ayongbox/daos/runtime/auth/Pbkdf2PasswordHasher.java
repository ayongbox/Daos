package io.github.ayongbox.daos.runtime.auth;

import io.github.ayongbox.daos.core.auth.PasswordHasher;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2-HmacSHA256 密码哈希。编码格式：{@code pbkdf2-sha256$iterations$salt$hash}。
 *
 * <p>每次哈希使用独立 16 字节盐，校验走常量时间比较，编码中带迭代次数以便未来平滑升级。</p>
 */
public final class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int MIN_ITERATIONS = 100_000;
    private static final int DEFAULT_ITERATIONS = 210_000;

    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    public Pbkdf2PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    public Pbkdf2PasswordHasher(int iterations) {
        if (iterations < MIN_ITERATIONS) {
            throw new IllegalArgumentException("PBKDF2 迭代次数不得低于 " + MIN_ITERATIONS);
        }
        this.iterations = iterations;
    }

    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = derive(rawPassword, salt, iterations);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(key);
    }

    @Override
    public boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || rawPassword.isEmpty() || encoded == null) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int storedIterations = Integer.parseInt(parts[1]);
            if (storedIterations < MIN_ITERATIONS) {
                return false;
            }
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] expected = decoder.decode(parts[3]);
            byte[] actual = derive(rawPassword, salt, storedIterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static byte[] derive(String rawPassword, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException error) {
            throw new IllegalStateException("PBKDF2 不可用", error);
        } finally {
            spec.clearPassword();
        }
    }
}
