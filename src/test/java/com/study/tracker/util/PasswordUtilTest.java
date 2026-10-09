package com.study.tracker.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("密码加盐哈希")
class PasswordUtilTest {

    @Test
    @DisplayName("同一个密码配同一个盐，摘要稳定可复现")
    void hashIsStableForSameSalt() {
        String salt = PasswordUtil.newSalt();
        assertEquals(PasswordUtil.hash("abc123", salt), PasswordUtil.hash("abc123", salt));
    }

    @Test
    @DisplayName("相同的密码配不同的盐，摘要不同")
    void differentSaltProducesDifferentHash() {
        assertNotEquals(PasswordUtil.hash("abc123", PasswordUtil.newSalt()),
                PasswordUtil.hash("abc123", PasswordUtil.newSalt()));
    }

    @Test
    @DisplayName("摘要是 64 位十六进制，盐是 32 位十六进制，且都不含明文")
    void hashAndSaltHaveExpectedFormat() {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash("abc123", salt);

        assertEquals(32, salt.length());
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertFalse(hash.contains("abc123"));
    }

    @Test
    @DisplayName("只有正确的密码才能通过校验；未设置密码时一律不通过")
    void verifyOnlyAcceptsCorrectPassword() {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash("abc123", salt);

        assertTrue(PasswordUtil.verify("abc123", salt, hash));
        assertFalse(PasswordUtil.verify("abc124", salt, hash));
        assertFalse(PasswordUtil.verify("abc123", salt, ""));
        assertFalse(PasswordUtil.verify("abc123", salt, null));
    }
}
