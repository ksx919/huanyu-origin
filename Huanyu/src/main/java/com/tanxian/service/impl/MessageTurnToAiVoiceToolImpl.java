package com.tanxian.service.impl;

import com.tanxian.service.MessageTurnToAiVoiceTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class MessageTurnToAiVoiceToolImpl implements MessageTurnToAiVoiceTool {

    private static final Logger LOG = LoggerFactory.getLogger(MessageTurnToAiVoiceToolImpl.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final int MAX_RETRIES = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_STREAM_TIMEOUT = Duration.ofSeconds(10);
    private static final long INITIAL_BACKOFF_MS = 300;

    @Override
    public byte[] turnToAiVoice(String message,String sessionId) {
        char type = sessionId.charAt(sessionId.length()-1);
        int port;
        switch (type){
            case '0':
                port = 5000;
                break;
            case '1':
                port = 5001;
                break;
            case '2':
                port = 5002;
                break;
            default:
                LOG.warn("未识别的type: {}，默认使用5000端口", type);
                port = 5000;
        }

        String url = "http://localhost:" + port + "/";

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            payload.put("text_language", "zh");
            String json = objectMapper.writeValueAsString(payload);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            long backoff = INITIAL_BACKOFF_MS;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    int status = response.statusCode();
                    if (status == 200) {
                        return response.body();
                    }
                    // 对5xx/429尝试重试，其它状态直接返回
                    if (status >= 500 || status == 429) {
                        LOG.warn("TTS服务响应{}，第{}次重试", status, attempt);
                    } else {
                        LOG.warn("TTS服务响应非200: {}，不重试", status);
                        return new byte[0];
                    }
                } catch (Exception e) {
                    if (attempt == MAX_RETRIES) {
                        LOG.error("调用TTS服务失败(最终尝试): url={}, type={}, sessionId={}", url, type, sessionId, e);
                        return new byte[0];
                    }
                    LOG.warn("调用TTS服务异常，第{}次重试：{}", attempt, e.toString());
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new byte[0];
                }
                backoff *= 2; // 指数退避
            }
            return new byte[0];
        } catch (Exception e) {
            LOG.error("调用TTS服务失败: url={}, type={}, sessionId={}", url, type, sessionId, e);
            return new byte[0];
        }
    }

    @Override
    public InputStream streamToAiVoice(String message, String sessionId) {
        char type = sessionId.charAt(sessionId.length() - 1);
        int port;
        switch (type) {
            case '0':
                port = 5000;
                break;
            case '1':
                port = 5001;
                break;
            case '2':
                port = 5002;
                break;
            default:
                LOG.warn("未识别的type: {}，默认使用5000端口", type);
                port = 5000;
        }

        String url = "http://localhost:" + port + "/stream";

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            payload.put("text_language", "zh");
            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_STREAM_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            long backoff = INITIAL_BACKOFF_MS;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    int status = response.statusCode();
                    if (status == 200) {
                        return response.body();
                    }
                    if (status >= 500 || status == 429) {
                        LOG.warn("TTS流服务响应{}，第{}次重试", status, attempt);
                    } else {
                        LOG.warn("TTS流服务响应非200: {}，不重试", status);
                        return new ByteArrayInputStream(new byte[0]);
                    }
                } catch (Exception e) {
                    if (attempt == MAX_RETRIES) {
                        LOG.error("调用TTS流服务失败(最终尝试): url={}, type={}, sessionId={}", url, type, sessionId, e);
                        return new ByteArrayInputStream(new byte[0]);
                    }
                    LOG.warn("调用TTS流服务异常，第{}次重试：{}", attempt, e.toString());
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new ByteArrayInputStream(new byte[0]);
                }
                backoff *= 2;
            }
            return new ByteArrayInputStream(new byte[0]);
        } catch (Exception e) {
            LOG.error("调用TTS流服务失败: url=/stream, type={}, sessionId={}", type, sessionId, e);
            return new ByteArrayInputStream(new byte[0]);
        }
    }
}
