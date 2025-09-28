package com.tanxian.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.tanxian.config.AliyunNlsProperties;
import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.tanxian.service.AiChatService;
import com.tanxian.service.MessageTurnToAiVoiceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket实时语音转文字处理器
 * 
 * 该类负责处理通过WebSocket传输的实时音频数据，将其转换为文字，并与AI聊天服务集成，
 * 实现语音到文本的实时转录，并触发AI回复。主要功能包括：
 * 1. 通过WebSocket接收实时音频流数据
 * 2. 调用阿里云ASR服务进行实时语音识别
 * 3. 将识别结果传递给AI聊天服务生成回复
 * 4. 将AI回复通过TTS转换为语音并发送回客户端
 * 
 * 该类以原型作用域(@Scope("prototype"))注册为Spring Bean，为每个WebSocket会话创建独立实例
 */
@Service
@Scope("prototype")
public class WebSocketSpeechTranscriber {
    @Autowired
    AiChatService aiChatService;
    @Autowired
    MessageTurnToAiVoiceTool messageTurnToAiVoiceTool;
    @Autowired
    public WebSocketSpeechTranscriber(AliyunNlsProperties props) {
        cnt = 0;
        isRecording = false;
        this.appKey = props.getAppKey();
        this.id = props.getAccessKeyId();
        this.secret = props.getAccessKeySecret();
        this.url = (props.getGatewayUrl() == null || props.getGatewayUrl().isBlank())
                ? "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1"
                : props.getGatewayUrl();
        this.customizationId = props.getCustomizationId() == null ? "" : props.getCustomizationId().trim();
        this.vocabularyId = props.getVocabularyId() == null ? "" : props.getVocabularyId().trim();

        AccessToken accessToken = new AccessToken(this.id, this.secret);
        try {
            accessToken.apply();
            System.out.println("get token: " + ", expire time: " + accessToken.getExpireTime());
            if (this.url.isBlank()) {
                client = new NlsClient(accessToken.getToken());
            } else {
                client = new NlsClient(this.url, accessToken.getToken());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String appKey,id,secret,url,sessionId;
    // 可选：阿里云定制模型与热词词表ID（通过配置注入）
    private String customizationId;
    private String vocabularyId;
    private NlsClient client;
    private static final Logger logger = LoggerFactory.getLogger(WebSocketSpeechTranscriber.class);
    public boolean isRecording;
    public SpeechTranscriber transcriber;
    public int cnt;

    private volatile WebSocketSession boundSession;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // Ensure websocket writes are serialized per session
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    // Interrupt flag for stopping current TTS audio streaming
    private volatile boolean stopAudioStreaming = false;
    // 串行处理音频播放，避免并发交错
    private final java.util.concurrent.ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ConcurrentLinkedQueue<String> ttsQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile boolean audioStreamingActive = false;
    // 句末回调去重：避免同一句被重复处理两次
    private final java.util.Set<String> processedSentenceIds = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    // 移除无参构造函数，防止绕过配置导致使用硬编码的凭证

    private SpeechTranscriberListener getTranscriberListener() {

        SpeechTranscriberListener listener = new SpeechTranscriberListener() {
            //识别出中间结果。仅当setEnableIntermediateResult为true时，才会返回该消息。
            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() +
                        ", name: " + response.getName() +
                        //状态码“20000000”表示正常识别。
                        ", status: " + response.getStatus() +
                        //句子编号，从1开始递增。
                        ", index: " + response.getTransSentenceIndex() +
                        //当前的识别结果。
                        ", result: " + response.getTransSentenceText() +
                        //当前已处理的音频时长，单位为毫秒。
                        ", time: " + response.getTransSentenceTime());
            }



            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                //task_id是调用方和服务端通信的唯一标识，遇到问题时，需要提供此task_id。
                System.out.println("task_id: " + response.getTaskId() + ", name: " + response.getName() + ", status: " + response.getStatus());
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() + ", name: " + response.getName() + ", status: " + response.getStatus());

            }

            //识别出一句话。服务端会智能断句，当识别到一句话结束时会返回此消息。
            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() +
                        ", name: " + response.getName() +
                        //状态码“20000000”表示正常识别。
                        ", status: " + response.getStatus() +
                        //句子编号，从1开始递增。
                        ", index: " + response.getTransSentenceIndex() +
                        //当前的识别结果。
                        ", result: " + response.getTransSentenceText() +
                        //置信度
                        ", confidence: " + response.getConfidence() +
                        //开始时间
                        ", begin_time: " + response.getSentenceBeginTime() +
                        //当前已处理的音频时长，单位为毫秒。
                        ", time: " + response.getTransSentenceTime());

                // 去重：同一task/index + 文本 只处理一次
                String sentenceKey = response.getTaskId() + "-" + response.getTransSentenceIndex() + ":" + String.valueOf(response.getTransSentenceText()).trim();
                if (!processedSentenceIds.add(sentenceKey)) {
                    logger.debug("Skip duplicated sentence: {}", sentenceKey);
                    return;
                }

                short characterId = (short) (sessionId.charAt(sessionId.length() - 1)-'0');
                System.out.println("lwj会话id是:"+sessionId);
                // 移除重复调用：避免通过 SendToAiTool 再次触发 aiChatService.chat

                // Stream AI text to frontend via WebSocket and then TTS audio
                try {
                    final WebSocketSession session = boundSession;
                    if (session != null && session.isOpen()) {
                        String userText = response.getTransSentenceText();
                        final StringBuilder aiTextBuilder = new StringBuilder();

                        aiChatService.chat(sessionId, userText, characterId)
                                .subscribe(
                                        chunk -> {
                                            aiTextBuilder.append(chunk);
                                            // 文本分片立即推送到前端
                                            Map<String, Object> msg = new HashMap<>();
                                            msg.put("type", "ai_text");
                                            msg.put("chunk", chunk);
                                            safeSendText(session, msg);

                                            // 从累计文本中提取可播放的分句，尽早启动TTS
                                            for (String seg : extractFlushableSegments(aiTextBuilder)) {
                                                enqueueTtsSegment(seg, sessionId);
                                            }
                                        },
                                        error -> {
                                            Map<String, Object> msg = new HashMap<>();
                                            msg.put("type", "ai_error");
                                            msg.put("message", String.valueOf(error.getMessage()));
                                            safeSendText(session, msg);
                                        },
                                        () -> {
                                            // 完成时若仍有剩余，作为最后一段播放
                                            String remainder = aiTextBuilder.toString().trim();
                                            if (!remainder.isEmpty()) {
                                                enqueueTtsSegment(remainder, sessionId);
                                            }
                                        }
                                );
                    }
                } catch (Exception e) {
                    logger.error("Error streaming AI/TTS via WebSocket", e);
                }

            }

            //识别完毕
            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                System.out.println("task_id: " + response.getTaskId() + ", name: " + response.getName() + ", status: " + response.getStatus());
                // 清理去重状态，避免下次会话受到影响
                processedSentenceIds.clear();
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                //task_id是调用方和服务端通信的唯一标识，遇到问题时，需要提供此task_id。
                System.out.println("task_id: " + response.getTaskId() +  ", status: " + response.getStatus() + ", status_text: " + response.getStatusText());
            }
        };

        return listener;
    }

    public void bindSession(WebSocketSession session) {
        this.boundSession = session;
    }

    private boolean isSessionOpen(WebSocketSession session) {
        return session != null && session.isOpen();
    }

    private void safeSendText(WebSocketSession session, Map<String, Object> payload) {
        if (!isSessionOpen(session)) return;
        try {
            String json = objectMapper.writeValueAsString(payload);
            // serialize writes via executor
            sendExecutor.execute(() -> {
                try {
                    if (isSessionOpen(session)) session.sendMessage(new TextMessage(json));
                } catch (Exception ex) {
                    logger.warn("WebSocket text send failed: {}", ex.toString());
                }
            });
        } catch (Exception e) {
            logger.warn("Serialize text message failed: {}", e.toString());
        }
    }

    private void streamTtsAndSend(String text, String sessionId) {
        final WebSocketSession session = boundSession;
        if (!isSessionOpen(session)) return;
        try (InputStream is = messageTurnToAiVoiceTool.streamToAiVoice(text, sessionId)) {
            // Notify audio start
            Map<String, Object> start = new HashMap<>();
            start.put("type", "audio_start");
            // 关键：同步发送audio_start，保证其在二进制音频之前到达前端，避免首帧被后续重置逻辑丢弃
            try {
                String json = objectMapper.writeValueAsString(start);
                if (isSessionOpen(session)) session.sendMessage(new TextMessage(json));
            } catch (Exception ex) {
                logger.warn("WebSocket text send failed (audio_start): {}", ex.toString());
            }

            // 使用更小的缓冲区以更快将音频分片推送到前端，降低首帧延迟
            byte[] buf = new byte[2048];
            int read;
            while ((read = is.read(buf)) != -1) {
                if (stopAudioStreaming) break;
                byte[] chunk = new byte[read];
                System.arraycopy(buf, 0, chunk, 0, read);
                // 直接发送二进制消息，避免排队带来的额外延迟
                try {
                    if (isSessionOpen(session)) session.sendMessage(new BinaryMessage(chunk));
                } catch (Exception ex) {
                    logger.warn("WebSocket binary send failed: {}", ex.toString());
                }
            }

            // Notify audio end
            Map<String, Object> end = new HashMap<>();
            end.put("type", "audio_end");
            // 同步发送audio_end，保持与audio_start一致的顺序保证
            try {
                String json = objectMapper.writeValueAsString(end);
                if (isSessionOpen(session)) session.sendMessage(new TextMessage(json));
            } catch (Exception ex) {
                logger.warn("WebSocket text send failed (audio_end): {}", ex.toString());
            }
            stopAudioStreaming = false;
        } catch (Exception e) {
            logger.error("TTS streaming failed", e);
            Map<String, Object> err = new HashMap<>();
            err.put("type", "audio_error");
            err.put("message", String.valueOf(e.getMessage()));
            safeSendText(session, err);
        }
    }

    // 将累计的AI文本按句号/问号/感叹号等分割，或达到长度阈值时切片
    private java.util.List<String> extractFlushableSegments(StringBuilder builder) {
        java.util.List<String> segments = new java.util.ArrayList<>();
        int lastCut = 0;
        for (int i = 0; i < builder.length(); i++) {
            char c = builder.charAt(i);
            boolean isBoundary = (c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '…' || c == '\n');
            if (isBoundary) {
                int end = i + 1;
                if (end - lastCut > 0) {
                    segments.add(builder.substring(lastCut, end).trim());
                }
                lastCut = end;
            }
        }
        // 长度兜底：若没有边界但长度超过一定阈值，提前切片
        final int MIN_CHARS = 40;
        if (segments.isEmpty() && builder.length() - lastCut >= MIN_CHARS) {
            segments.add(builder.substring(lastCut).trim());
            lastCut = builder.length();
        }
        // 从builder中删除已切分的部分
        if (lastCut > 0) {
            builder.delete(0, lastCut);
        }
        return segments;
    }

    // 入队并触发串行播放
    private void enqueueTtsSegment(String segment, String sessionId) {
        if (segment == null || segment.isBlank()) return;
        ttsQueue.offer(segment);
        if (!audioStreamingActive) {
            audioStreamingActive = true;
            audioExecutor.execute(() -> {
                try {
                    while (isSessionOpen(boundSession)) {
                        String seg = ttsQueue.poll();
                        if (seg == null) break;
                        streamTtsAndSend(seg, sessionId);
                    }
                } finally {
                    audioStreamingActive = false;
                }
            });
        }
    }

    /**
     * 中断当前AI语音播放
     * 
     * 该方法用于打断当前正在进行的TTS语音播放，但不会停止ASR录音过程。
     * 适用于用户希望立即停止AI语音输出的场景，例如用户开始说话时自动打断AI语音。
     */
    public void interrupt() {
        stopAudioStreaming = true;
    }

    /**
     * 根据二进制数据大小计算对应的同等语音长度
     * 
     * @param dataSize 数据大小（字节）
     * @param sampleRate 采样率，支持8000或16000
     * @return 对应的睡眠时间（毫秒）
     */
    public static int getSleepDelta(int dataSize, int sampleRate) {
        // 仅支持16位采样。
        int sampleBytes = 16;
        // 仅支持单通道。
        int soundChannel = 1;
        return (dataSize * 10 * 8000) / (160 * sampleRate);
    }

    /**
     * 处理本地音频文件
     * 
     * 该方法用于处理本地存储的音频文件，将其发送到阿里云ASR服务进行批量语音识别。
     * 主要用于测试或处理预先录制的音频文件。
     * 
     * @param filepath 音频文件路径
     */
    public void process(String filepath) {
        try {
            //创建实例、建立连接。
            transcriber = new SpeechTranscriber(client, getTranscriberListener());
            transcriber.setAppKey(appKey);
            //输入音频编码方式。
            transcriber.setFormat(InputFormatEnum.PCM);
            //输入音频采样率。
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            //是否返回中间识别结果。
            transcriber.setEnableIntermediateResult(false);
            //是否生成并返回标点符号。
            transcriber.setEnablePunctuation(true);
            //是否将返回结果规整化，比如将一百返回为100。
            transcriber.setEnableITN(false);

            //设置vad断句参数。默认值：800ms，有效值：200ms～6000ms。
            //transcriber.addCustomedParam("max_sentence_silence", 600);
            //设置是否语义断句。
            //transcriber.addCustomedParam("enable_semantic_sentence_detection",false);
            //设置是否开启过滤语气词，即声音顺滑。
            //transcriber.addCustomedParam("disfluency",true);
            //设置是否开启词模式。
            //transcriber.addCustomedParam("enable_words",true);
            //设置vad噪音阈值参数，参数取值为-1～+1，如-0.9、-0.8、0.2、0.9。
            //取值越趋于-1，判定为语音的概率越大，亦即有可能更多噪声被当成语音被误识别。
            //取值越趋于+1，判定为噪音的越多，亦即有可能更多语音段被当成噪音被拒绝识别。
            //该参数属高级参数，调整需慎重和重点测试。
            //transcriber.addCustomedParam("speech_noise_threshold",0.3);
            //设置训练后的定制语言模型id。
            //transcriber.addCustomedParam("customization_id","你的定制语言模型id");
            //设置训练后的定制热词id。
            //transcriber.addCustomedParam("vocabulary_id","你的定制热词id");

            // 应用定制模型与热词词表（如已配置）
            applyHotwordAndCustomization(transcriber);
            //此方法将以上参数设置序列化为JSON发送给服务端，并等待服务端确认。
            transcriber.start();

            File file = new File(filepath);
            FileInputStream fis = new FileInputStream(file);
            byte[] b = new byte[3200];
            int len;
            while ((len = fis.read(b)) > 0) {
                logger.info("send data pack length: " + len);
                transcriber.send(b, len);
                //本案例用读取本地文件的形式模拟实时获取语音流并发送的，因为读取速度较快，这里需要设置sleep。
                //如果实时获取语音则无需设置sleep, 如果是8k采样率语音第二个参数设置为8000。
                int deltaSleep = getSleepDelta(len, 16000);
                Thread.sleep(deltaSleep);
            }

            //通知服务端语音数据发送完毕，等待服务端处理完成。
            long now = System.currentTimeMillis();
            logger.info("ASR wait for complete");
            transcriber.stop();
            logger.info("ASR latency : " + (System.currentTimeMillis() - now) + " ms");
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            transcriber.close();
        }
    }


    /**
     * 处理通过WebSocket接收到的音频数据
     * 
     * 该方法接收实时音频数据块并发送到阿里云ASR服务进行语音识别。
     * 如果是第一次调用，会初始化ASR连接和相关参数。
     * 
     * @param data 音频数据块
     * @param sessionId 会话ID，用于标识不同的用户会话
     */
    public void process(byte[] data,String sessionId) {
        try {
            this.sessionId = sessionId;
            if(!isRecording) {
                isRecording = true;
                //创建实例、建立连接。
                transcriber = new SpeechTranscriber(client, getTranscriberListener());
                transcriber.setAppKey(appKey);
                //输入音频编码方式。
                transcriber.setFormat(InputFormatEnum.PCM);
                //输入音频采样率。
                transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
                //是否返回中间识别结果。
                transcriber.setEnableIntermediateResult(false);
                //是否生成并返回标点符号。
                transcriber.setEnablePunctuation(true);
                //是否将返回结果规整化，比如将一百返回为100。
                transcriber.setEnableITN(false);

                //设置vad断句参数。默认值：800ms，有效值：200ms～6000ms。
                //transcriber.addCustomedParam("max_sentence_silence", 600);
                //设置是否语义断句。
                //transcriber.addCustomedParam("enable_semantic_sentence_detection",false);
                //设置是否开启过滤语气词，即声音顺滑。
                //transcriber.addCustomedParam("disfluency",true);
                //设置是否开启词模式。
                //transcriber.addCustomedParam("enable_words",true);
                //设置vad噪音阈值参数，参数取值为-1～+1，如-0.9、-0.8、0.2、0.9。
                //取值越趋于-1，判定为语音的概率越大，亦即有可能更多噪声被当成语音被误识别。
                //取值越趋于+1，判定为噪音的越多，亦即有可能更多语音段被当成噪音被拒绝识别。
                //该参数属高级参数，调整需慎重和重点测试。
                //transcriber.addCustomedParam("speech_noise_threshold",0.3);
                //设置训练后的定制语言模型id。
                //transcriber.addCustomedParam("customization_id","你的定制语言模型id");
                //设置训练后的定制热词id。
                //transcriber.addCustomedParam("vocabulary_id","你的定制热词id");

                // 应用定制模型与热词词表（如已配置）
                applyHotwordAndCustomization(transcriber);
                //此方法将以上参数设置序列化为JSON发送给服务端，并等待服务端确认。
                transcriber.start();
            }
            byte[] b = data;
            int len = b.length;
            logger.info("send data pack length: " + len);
            transcriber.send(b, len);
            //本案例用读取本地文件的形式模拟实时获取语音流并发送的，因为读取速度较快，这里需要设置sleep。
            //如果实时获取语音则无需设置sleep, 如果是8k采样率语音第二个参数设置为8000。
//            int deltaSleep = getSleepDelta(len, 16000);
//            Thread.sleep(deltaSleep);

            //通知服务端语音数据发送完毕，等待服务端处理完成。
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {

        }
    }

    /**
     * 应用定制模型与热词词表
     * 
     * 在ASR启动前应用定制语言模型和热词词表，以提高特定领域或术语的识别准确率。
     * 
     * @param transcriber 语音识别器实例
     */
    private void applyHotwordAndCustomization(SpeechTranscriber transcriber) {
        try {
            if (customizationId != null && !customizationId.isBlank()) {
                logger.info("Apply customization_id: {}", customizationId);
                transcriber.addCustomedParam("customization_id", customizationId);
            }
            if (vocabularyId != null && !vocabularyId.isBlank()) {
                logger.info("Apply vocabulary_id: {}", vocabularyId);
                transcriber.addCustomedParam("vocabulary_id", vocabularyId);
            }
        } catch (Exception ex) {
            logger.warn("Apply hotword/customization failed: {}", ex.toString());
        }
    }

    public void shutdown() {
        client.shutdown();
        try {
            sendExecutor.shutdownNow();
            sendExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
    }

}
