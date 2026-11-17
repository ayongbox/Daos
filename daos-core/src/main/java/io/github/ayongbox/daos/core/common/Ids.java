package io.github.ayongbox.daos.core.common;

import java.security.SecureRandom;

/**
 * 元数据主键生成器。ULID：48 位毫秒时间戳 + 80 位随机数，Crockford Base32 编码为 26 字符。
 *
 * <p>同一毫秒内通过随机段自增保证严格单调，快照 id 可直接按字典序排序。</p>
 */
public final class Ids {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int RANDOM_BYTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static long lastTimestamp = -1;
    private static final byte[] lastRandom = new byte[RANDOM_BYTES];

    private Ids() {
    }

    public static synchronized String ulid() {
        long now = System.currentTimeMillis();
        if (now > lastTimestamp) {
            lastTimestamp = now;
            RANDOM.nextBytes(lastRandom);
        } else {
            now = lastTimestamp;
            if (!increment(lastRandom)) {
                // 80 位随机段耗尽（实际不可能发生），等待下一毫秒重新取随机段。
                lastTimestamp = lastTimestamp + 1;
                now = lastTimestamp;
                RANDOM.nextBytes(lastRandom);
            }
        }
        return encode(now, lastRandom);
    }

    public static boolean isUlid(String value) {
        if (value == null || value.length() != 26) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            boolean valid = (current >= '0' && current <= '9') || "ABCDEFGHJKMNPQRSTVWXYZ".indexOf(current) >= 0;
            if (!valid) {
                return false;
            }
        }
        // 48 位时间戳只占 10 个字符的低 48 位，首字符不能超过 '7'。
        return value.charAt(0) <= '7';
    }

    private static boolean increment(byte[] value) {
        for (int index = value.length - 1; index >= 0; index--) {
            if (value[index] != (byte) 0xFF) {
                value[index]++;
                return true;
            }
            value[index] = 0;
        }
        return false;
    }

    private static String encode(long timestamp, byte[] random) {
        char[] result = new char[26];

        long time = timestamp;
        for (int index = 9; index >= 0; index--) {
            result[index] = ENCODING[(int) (time & 0x1F)];
            time >>>= 5;
        }

        int bitBuffer = 0;
        int bitCount = 0;
        int position = 10;
        for (byte current : random) {
            bitBuffer = (bitBuffer << 8) | (current & 0xFF);
            bitCount += 8;
            while (bitCount >= 5) {
                bitCount -= 5;
                result[position++] = ENCODING[(bitBuffer >>> bitCount) & 0x1F];
            }
        }
        return new String(result);
    }
}
