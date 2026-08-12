package com.logmonitor.log.security;

import java.util.regex.Pattern;

/**
 * 敏感信息脱敏工具：对日志/参数中的密码、令牌、手机号、身份证号打码。
 * <p>
 * 同时兼容 key=value 与 JSON 两种格式：
 *   password=123456 -> password=***
 *   "password":"123456" -> "password":"***"
 */
public final class SensitiveDataMasker {

    /** JSON 格式：{ "password": "xxx" } */
    private static final Pattern JSON_KEY_VALUE = Pattern.compile(
            "(?i)(\"(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key)\"\\s*:\\s*\")([^\"]*)(\")");

    /** key=value 格式：password=xxx / token: xxx */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key)\\b(\\s*[=:]\\s*)([^\\s,;&\"']+)");

    /** 身份证号：18 位（末位可为 X） */
    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[\\dXx]\\b");

    /** 手机号：1[3-9]xxxxxxxxx，保留前 3 后 4 */
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(1[3-9]\\d)(\\d{4})(\\d{4})(?![0-9])");

    private SensitiveDataMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = JSON_KEY_VALUE.matcher(text).replaceAll("$1***$3");
        masked = KEY_VALUE.matcher(masked).replaceAll("$1$2***");
        masked = ID_CARD.matcher(masked).replaceAll("***************");
        masked = PHONE.matcher(masked).replaceAll("$1****$3");
        return masked;
    }
}
