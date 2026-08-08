package com.logmonitor.log.config;

import com.logmonitor.log.security.AesGcmCryptoUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 敏感配置解密处理器：支持 application.yml 中 ENC(Base64密文) 前缀。
 * <p>
 * 解密密钥从环境变量 APP_ENC_KEY 读取（不落配置文件）；未使用 ENC() 的配置原样透传。
 * 使用方式：先用 AesGcmCryptoUtil 生成密文，再写入配置，例如
 *   spring.datasource.password: ENC(AbCdEf...)
 * <p>
 * 注册文件：META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor
 */
public class EncryptedConfigPostProcessor implements EnvironmentPostProcessor {

    private static final String ENC_PREFIX = "ENC(";
    private static final String KEY_ENV = "APP_ENC_KEY";

    /** 允许使用 ENC() 加密的敏感配置项白名单 */
    private static final Set<String> TARGETS = Set.of(
            "spring.datasource.password",
            "app.security.jwt-secret",
            "app.security.service-token",
            "spring.data.redis.password");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String encKey = environment.getProperty(KEY_ENV);
        MutablePropertySources sources = environment.getPropertySources();

        for (PropertySource<?> source : sources) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            Map<String, Object> decrypted = new HashMap<>();
            for (String name : enumerable.getPropertyNames()) {
                if (!TARGETS.contains(name)) {
                    continue;
                }
                Object value = enumerable.getProperty(name);
                if (!(value instanceof String text) || !text.startsWith(ENC_PREFIX) || !text.endsWith(")")) {
                    continue;
                }
                if (encKey == null || encKey.isBlank()) {
                    throw new IllegalStateException(
                            "配置项 " + name + " 使用了 ENC() 加密，但未设置环境变量 " + KEY_ENV);
                }
                String cipher = text.substring(ENC_PREFIX.length(), text.length() - 1);
                decrypted.put(name, AesGcmCryptoUtil.decrypt(cipher, encKey));
            }
            if (!decrypted.isEmpty()) {
                // 放在最前面，覆盖原始加密值
                sources.addFirst(new MapPropertySource("encrypted-config-" + source.getName(), decrypted));
            }
        }
    }
}
