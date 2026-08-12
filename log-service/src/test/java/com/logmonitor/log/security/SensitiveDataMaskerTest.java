package com.logmonitor.log.security;

import org.junit.jupiter.api.Test;

import static com.logmonitor.log.security.SensitiveDataMasker.mask;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 敏感信息脱敏测试：key=value、JSON、手机号、身份证、普通文本。
 */
class SensitiveDataMaskerTest {

    @Test
    void masksKeyValueStyle() {
        assertEquals("password=***", mask("password=123456"));
        assertEquals("token: ***", mask("token: abc123"));
    }

    @Test
    void masksJsonStyle() {
        assertEquals("{\"password\":\"***\"}", mask("{\"password\":\"123456\"}"));
    }

    @Test
    void masksPhoneAndIdCard() {
        assertEquals("138****5678", mask("13812345678"));
        assertEquals("***************", mask("110101199001011234"));
    }

    @Test
    void keepsNormalText() {
        assertEquals("hello world 2026", mask("hello world 2026"));
    }
}
