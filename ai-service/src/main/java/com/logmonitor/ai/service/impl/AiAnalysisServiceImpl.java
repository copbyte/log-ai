package com.logmonitor.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.AiAnalysisResult;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.ai.mapper.AiAnalysisResultMapper;
import com.logmonitor.ai.service.AiAnalysisService;
import com.logmonitor.ai.service.AiApiClient;
import com.logmonitor.ai.service.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiAnalysisServiceImpl extends ServiceImpl<AiAnalysisResultMapper, AiAnalysisResult>
        implements AiAnalysisService {

    private final AiApiClient aiApiClient;
    private final PromptService promptService;
    private final StringRedisTemplate redisTemplate;

    public AiAnalysisServiceImpl(AiApiClient aiApiClient,
                                  PromptService promptService,
                                  StringRedisTemplate redisTemplate) {
        this.aiApiClient = aiApiClient;
        this.promptService = promptService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AiAnalysisResult analyze(LogEntry logEntry) {
        String contentHash = hashContent(logEntry);
        String cacheKey = "log:monitor:ai:analysis:" + contentHash;

        // Check Redis cache
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for content hash: {}", contentHash);
            return null; // Already analyzed recently
        }

        String prompt = promptService.buildPrompt(logEntry);
        AnalysisResponse response = aiApiClient.analyze(prompt);

        AiAnalysisResult result = AiAnalysisResult.builder()
                .logEntryId(logEntry.getId())
                .summary(response.getSummary())
                .rootCause(response.getRootCause())
                .suggestion(response.getSuggestion())
                .modelName("stub-model")
                .tokensUsed(0)
                .build();

        save(result);

        // Cache for 30 minutes
        redisTemplate.opsForValue().set(cacheKey, result.getId().toString(), 30, TimeUnit.MINUTES);

        log.info("AI analysis completed: logEntryId={}, resultId={}", logEntry.getId(), result.getId());
        return result;
    }

    private String hashContent(LogEntry logEntry) {
        String input = logEntry.getLogLevel() + ":" + truncate(logEntry.getContent(), 200);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
