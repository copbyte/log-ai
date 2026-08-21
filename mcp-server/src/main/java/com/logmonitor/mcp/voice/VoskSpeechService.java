package com.logmonitor.mcp.voice;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.jna.Platform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.io.IOException;

@Slf4j
@Service
public class VoskSpeechService {

    @Value("${voice.model-path}")
    private String modelPath;

    @Value("${voice.wake-word}")
    private String wakeWord;

    private Model model;

    @PostConstruct
    public void init() throws IOException {
        // TODO 1: 加载 Vosk 模型
        // 提示: model = new Model(modelPath);
        // 注意: 模型加载耗时 2-5 秒，@PostConstruct 会阻塞启动，这是正常的
        this.model = new Model(resolveModelPath());
        log.info("Vosk model loaded from: {}", modelPath);
    }

    /**
     * 解析模型路径：兼容不同启动方式的工作目录。
     * 依次尝试：1) 直接可用 2) 项目根/mcp-server/models/... 3) user.dir/mcp-server/models/...
     */
    private String resolveModelPath() {
        String[] candidates = {
                modelPath,
                "mcp-server/" + modelPath.replaceFirst("^\\./", ""),
                System.getProperty("user.dir") + "/mcp-server/" + modelPath.replaceFirst("^\\./", "")
        };
        for (String candidate : candidates) {
            File dir = new File(candidate);
            if (dir.isDirectory() && new File(dir, "am/final.mdl").exists()) {
                log.info("Vosk model resolved at: {}", dir.getAbsolutePath());
                return dir.getAbsolutePath();
            }
        }
        // 都找不到就返回原始路径，让 Model 构造器报错（带清晰提示）
        log.warn("Vosk model not found, tried: {}", String.join(" | ", candidates));
        return modelPath;
    }

    public void destory(){
        if (model != null){
            model.close();
            log.info("vosk模型关闭");
        }
    }

    /**
     * 创建识别器（每个 WebSocket 连接一个）
     * 采样率 16000Hz，16bit，单声道
     */
    public Recognizer createRecognizer() {
        // TODO 2: 创建 Recognizer
        // 提示: return new Recognizer(model, 16000f);
        // 16000f 是采样率，要跟前端 AudioWorklet 输出的采样率一致
        try {
            return new Recognizer(model,16000f);
        } catch (IOException e) {
            log.error("创建vosk recognizer失败:",e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理一段音频，返回识别文本
     * @param audioData PCM 字节流（16bit little-endian）
     * @return 识别出的文本，空字符串表示还没识别完
     */

    public record RecognitionResult(boolean finalResult, String text) {
    }

    public RecognitionResult recognize(Recognizer recognizer, byte[] audioData) {
        // TODO 3: 流式识别
        // 提示:
        // 1. recognizer.acceptWaveForm(audioData, audioData.length)
        // 2. 如果返回 true，表示一句话结束，用 recognizer.getFinalResult() 拿完整结果
        // 3. 如果返回 false，用 recognizer.getPartialResult() 拿中间结果
        // 4. 结果是 JSON: {"text": "识别的文字"}
        // 5. 用 fastjson2 解析 JSON 拿 text 字段
        // 6. 记住每次用完要 recognizer.reset() 准备下一句（不对，reset 是手动控制的，这里不用）
        boolean finalResult = recognizer.acceptWaveForm(audioData, audioData.length);
        String jsonResult = finalResult ? recognizer.getFinalResult() : recognizer.getPartialResult();
        JSONObject jsonObject = JSON.parseObject(jsonResult);
        String s = jsonObject.getString(finalResult ? "text" : "partial");
        return new  RecognitionResult(finalResult, s == null ? "" : s);
    }

    /**
     * 判断文本是否包含唤醒词
     */
    public boolean isWakeWord(String text) {
        // TODO 4: 唤醒词匹配
        // 提示: text != null && text.contains(wakeWord)
        // 注意: Vosk 识别中文可能带空格或标点，比如 "小 日 志"，要做清理
        // 简单做法: text.replace(" ", "").contains(wakeWord.replace(" ", ""))
        if (text==null){
            return false;
        }
        String cleanText = text.replace(" ", "");
        String cleanWakeword = wakeWord.replace(" ", "");
        return cleanText.contains(cleanWakeword);
    }
}