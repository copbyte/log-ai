package com.logmonitor.log.config;

import com.logmonitor.log.security.AesGcmCryptoUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 加密配置解密测试：ENC() 解密、明文透传、缺少 APP_ENC_KEY 报错。
 */
class EncryptedConfigPostProcessorTest {

    private final EncryptedConfigPostProcessor processor = new EncryptedConfigPostProcessor();

    @Test
    void decryptsEncValues() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.password",
                "ENC(" + AesGcmCryptoUtil.encrypt("secret123", "test-key") + ")");
        env.setProperty("APP_ENC_KEY", "test-key");

        processor.postProcessEnvironment(env, null);

        assertEquals("secret123", env.getProperty("spring.datasource.password"));
    }

    @Test
    void plainValuesUntouched() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.password", "123456");

        processor.postProcessEnvironment(env, null);

        assertEquals("123456", env.getProperty("spring.datasource.password"));
    }

    @Test
    void missingKeyThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.password", "ENC(abc)");

        assertThrows(IllegalStateException.class,
                () -> processor.postProcessEnvironment(env, null));
    }
}
