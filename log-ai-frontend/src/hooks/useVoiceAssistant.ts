// 核心逻辑：
// 1. getUserMedia 拿麦克风
// 2. ScriptProcessorNode 采集 PCM
// 3. 降采样到 16kHz（浏览器默认 48kHz）
// 4. WebSocket 发送二进制音频（Int16 little-endian）
// 5. 接收后端 JSON 消息，更新状态

import { useState, useRef, useCallback, useEffect } from 'react';

export type VoiceState = 'idle' | 'listening' | 'wake' | 'processing' | 'speaking';

interface UseVoiceAssistant {
    state: VoiceState;
    partialText: string;
    response: string;
    start: () => Promise<void>;
    stop: () => void;
    isSupported: boolean;
}

// WebSocket 地址：开发模式走 Vite proxy（/ws -> 8082），生产可用 VITE_WS_URL 覆盖
const WS_URL: string =
    (import.meta.env?.VITE_WS_URL as string | undefined) ??
    `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/voice`;

/** Float32 PCM → 16kHz Int16（线性插值降采样，Vosk 只认 16kHz 16bit 单声道） */
function downsampleTo16k(input: Float32Array, inputRate: number): Int16Array {
    const ratio = inputRate / 16000;
    const outLen = Math.floor(input.length / ratio);
    const out = new Int16Array(outLen);
    for (let i = 0; i < outLen; i++) {
        const src = i * ratio;
        const idx = Math.floor(src);
        const frac = src - idx;
        const next = Math.min(idx + 1, input.length - 1);
        const sample = input[idx] * (1 - frac) + input[next] * frac;
        out[i] = Math.max(-32768, Math.min(32767, sample * 32767));
    }
    return out;
}

export function useVoiceAssistant(onAIResponse?: (text: string) => void): UseVoiceAssistant {
    const [state, setState] = useState<VoiceState>('idle');
    const [partialText, setPartialText] = useState('');
    const [response, setResponse] = useState('');

    const wsRef = useRef<WebSocket | null>(null);
    const audioContextRef = useRef<AudioContext | null>(null);
    const streamRef = useRef<MediaStream | null>(null);
    const processorRef = useRef<ScriptProcessorNode | null>(null);
    const sourceRef = useRef<MediaStreamAudioSourceNode | null>(null);
    const activeRef = useRef(false);   // 会话是否激活（用于播报结束后决定是否恢复采集）

    const isSupported = typeof navigator !== 'undefined'
        && navigator.mediaDevices
        && typeof AudioContext !== 'undefined';

    /** 中文 TTS 播报：播报期间暂停麦克风采集，避免 TTS 声音被收进去触发误识别 */
    const speak = useCallback((text: string) => {
        try {
            if (typeof window === 'undefined' || !('speechSynthesis' in window)) return;
            const wasCollecting = processorRef.current !== null;
            if (wasCollecting) {
                processorRef.current?.disconnect();   // 暂停采集（onaudioprocess 不再触发）
            }
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = 'zh-CN';
            utterance.rate = 1.0;
            utterance.onend = () => {
                // 会话仍激活且 WS 仍打开 → 恢复采集
                if (activeRef.current && wsRef.current?.readyState === WebSocket.OPEN
                    && sourceRef.current && processorRef.current && audioContextRef.current) {
                    sourceRef.current.connect(processorRef.current);
                    processorRef.current.connect(audioContextRef.current.destination);
                }
            };
            window.speechSynthesis.speak(utterance);
        } catch (e) {
            console.warn('TTS 播报失败:', e);
        }
    }, []);

    const stop = useCallback(() => {
        // 清理资源：断开处理器、停麦克风、关音频上下文、关 WS、停止播报
        activeRef.current = false;
        if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
            window.speechSynthesis.cancel();
        }
        processorRef.current?.disconnect();
        streamRef.current?.getTracks().forEach((t) => t.stop());
        audioContextRef.current?.close();
        wsRef.current?.close();
        wsRef.current = null;
        processorRef.current = null;
        sourceRef.current = null;
        streamRef.current = null;
        audioContextRef.current = null;
        setState('idle');
    }, []);

    const start = useCallback(async () => {
        if (!isSupported || state !== 'idle') return; // 防止重复启动

        try {
            // 1. 拿麦克风
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            streamRef.current = stream;
            activeRef.current = true;

            // 2. 创建 AudioContext（用默认采样率，通常 48kHz，之后手动降采样到 16kHz）
            const audioContext = new AudioContext();
            audioContextRef.current = audioContext;
            const source = audioContext.createMediaStreamSource(stream);
            sourceRef.current = source;
            const processor = audioContext.createScriptProcessor(4096, 1, 1);
            processorRef.current = processor;

            // 3. 采集回调：Float32 → 降采样 16kHz → Int16 → WS 发送
            processor.onaudioprocess = (e) => {
                const pcmData = e.inputBuffer.getChannelData(0);
                const int16 = downsampleTo16k(pcmData, audioContext.sampleRate);
                if (wsRef.current?.readyState === WebSocket.OPEN) {
                    wsRef.current.send(int16.buffer);
                }
            };
            source.connect(processor);
            processor.connect(audioContext.destination);

            // 4. 建立 WebSocket 连接
            const ws = new WebSocket(WS_URL);
            ws.binaryType = 'arraybuffer';
            ws.onopen = () => {
                setState('listening');
                // 语音服务可用性播报：能听到说明 麦克风授权 + WS 连接 + TTS 全链路 OK
                speak('语音服务已连接，请说小日唤醒我');
            };
            ws.onmessage = (e) => {
                try {
                    const msg = JSON.parse(e.data);
                    switch (msg.type) {
                        case 'status':   setState((msg.state ?? msg.text) as VoiceState); break;
                        case 'wake':
                            setState('wake');
                            // 唤醒词识别链路 OK：让用户知道现在可以提问了
                            speak('我在，请说出你的问题');
                            break;
                        case 'partial':  setPartialText(msg.text ?? ''); break;
                        case 'response':
                            setResponse(msg.text ?? '');
                            onAIResponse?.(msg.text ?? '');
                            break;
                        case 'error':    console.error('语音错误:', msg.text); break;
                        default:         break;
                    }
                } catch (err) {
                    console.error('解析后端消息失败:', err);
                }
            };
            ws.onclose = () => setState('idle');
            wsRef.current = ws;

            setState('listening');
        } catch (e) {
            console.error('启动语音失败:', e);
            stop();
        }
    }, [isSupported, state, stop, speak]);

    // 组件卸载时清理
    useEffect(() => {
        return () => {
            stop();
        };
    }, [stop]);

    return { state, partialText, response, start, stop, isSupported };
}
