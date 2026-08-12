package com.logmonitor.log.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AES-GCM 加解密测试：往返、随机 IV、错误密钥。
 */
class AesGcmCryptoUtilTest {

    @Test
    void encryptThenDecryptReturnsPlaintext() {
        String cipher = AesGcmCryptoUtil.encrypt("123456", "my-key");
        assertNotEquals("123456", cipher);
        assertEquals("123456", AesGcmCryptoUtil.decrypt(cipher, "my-key"));
    }

    @Test
    void samePlaintextProducesDifferentCiphertext() {
        String c1 = AesGcmCryptoUtil.encrypt("secret", "key");
        String c2 = AesGcmCryptoUtil.encrypt("secret", "key");
        assertNotEquals(c1, c2);
    }

    @Test
    void wrongKeyFails() {
        String cipher = AesGcmCryptoUtil.encrypt("123456", "right-key");
        assertThrows(IllegalStateException.class, () -> AesGcmCryptoUtil.decrypt(cipher, "wrong-key"));
    }
}
