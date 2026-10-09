package com.study.tracker.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 密码哈希工具。
 * <p>
 * 采用"随机盐 + SHA-256"方案：注册时生成 16 字节随机盐，
 * 数据库只保存盐和 {@code SHA-256(密码 + 盐)} 的十六进制摘要，不保存明文。
 * 校验时用同样的盐重新计算，并用 {@link MessageDigest#isEqual} 做恒定时间比较，
 * 避免逐字节比较带来的时序差异。
 */
public final class PasswordUtil {

    /** 盐长度（字节）。 */
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    /** 生成一个新的随机盐，返回十六进制字符串。 */
    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * 计算密码摘要。
     *
     * @param password 明文密码
     * @param salt     该用户的盐
     * @return SHA-256(密码 + 盐) 的十六进制字符串
     */
    public static String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
            digest.update((salt == null ? "" : salt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256 算法", e);
        }
    }

    /**
     * 校验密码是否匹配。
     *
     * @param password     待校验的明文密码
     * @param salt         该用户的盐
     * @param expectedHash 数据库里保存的摘要
     * @return 匹配返回 true；摘要为空（尚未设置密码）时一律返回 false
     */
    public static boolean verify(String password, String salt, String expectedHash) {
        if (expectedHash == null || expectedHash.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(password, salt).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
