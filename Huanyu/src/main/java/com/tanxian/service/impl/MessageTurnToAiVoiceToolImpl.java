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
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final int MAX_RETRIES = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_STREAM_TIMEOUT = Duration.ofSeconds(30);
    private static final long INITIAL_BACKOFF_MS = 300;

    @Override
    public byte[] turnToAiVoice(String message,String sessionId) {
        message = message.replaceAll("\\*.*\\*", "");
        char type = sessionId.charAt(sessionId.length()-1);
        int port = switch (type) {
            case '0' -> 5000;
            case '1' -> 5001;
            case '2' -> 5002;
            default -> {
                LOG.warn("未识别的type: {}，默认使用5000端口", type);
                yield 5000;
            }
        };

        // 使用IPv4避免Windows上localhost解析为IPv6(::1)导致连接失败
        String base = "http://127.0.0.1:" + port;
        String url = base + "/";     // 仅使用 api.py 的根路径端点

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            // api.py 使用 text_language
            payload.put("text_language", "zh");
            String json = objectMapper.writeValueAsString(payload);

            // 构建请求体，确保JSON格式正确
            byte[] jsonBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "audio/wav")
                    .header("User-Agent", "Java-TTS-Client/1.0")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes))
                    .build();

            // 回退到 POST /stream（部分api.py版本只提供POST /stream）
            String streamUrl = base + "/stream";
            HttpRequest requestStreamPost = HttpRequest.newBuilder()
                    .uri(URI.create(streamUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "audio/wav")
                    .header("User-Agent", "Java-TTS-Client/1.0")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes))
                    .build();

            LOG.info("发送TTS请求: url={}, message={}, sessionId={}, json={}, jsonBytes.length={}",
                    url, message, sessionId, json, jsonBytes.length);
            
            // 添加详细的请求调试信息，包括UTF-8字节内容
            LOG.info("请求详情: Content-Type=application/json; charset=utf-8, Accept=audio/wav, Body={}", json);
            LOG.info("UTF-8字节内容: {}", java.util.Arrays.toString(java.util.Arrays.copyOf(jsonBytes, Math.min(100, jsonBytes.length))));
            
            long backoff = INITIAL_BACKOFF_MS;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    int status = response.statusCode();
                    LOG.info("TTS请求响应: status={}, attempt={}, url={}, sessionId={}, responseSize={} bytes", 
                            status, attempt, url, sessionId, response.body().length);
                    
                    // 记录响应头信息
                    response.headers().map().forEach((key, values) -> 
                        LOG.info("响应头: {}={}", key, String.join(",", values)));
                    
                    if (status == 200) {
                        LOG.info("TTS请求成功: url={}, sessionId={}, responseSize={} bytes", url, sessionId, response.body().length);
                        return response.body();
                    }
                    
                    // 记录错误响应内容
                    if (response.body().length > 0) {
                        String errorBody = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
                        LOG.warn("TTS错误响应内容: status={}, body={}", status, errorBody);
                    }
                    // 尝试 POST /stream 作为回退
                    try {
                        HttpResponse<byte[]> resp2 = httpClient.send(requestStreamPost, HttpResponse.BodyHandlers.ofByteArray());
                        int st2 = resp2.statusCode();
                        LOG.info("TTS回退到/stream响应: status={}, attempt={}, url={}, sessionId={}", st2, attempt, streamUrl, sessionId);
                        if (st2 == 200) {
                            LOG.info("TTS回退到/stream成功: url={}, sessionId={}, responseSize={} bytes", streamUrl, sessionId, resp2.body().length);
                            return resp2.body();
                        }
                    } catch (Exception ex2) {
                        LOG.warn("TTS回退到/stream异常: {}", ex2.toString());
                    }
                    // 对5xx/429尝试重试，其它状态直接返回
                    if (status >= 500 || status == 429) {
                        LOG.warn("TTS服务响应{}，第{}次重试", status, attempt);
                    } else {
                        LOG.warn("TTS服务响应非200: {}，不重试", status);
                        return new byte[0];
                    }
                } catch (java.net.ConnectException e) {
                    if (attempt == MAX_RETRIES) {
                        LOG.error("调用TTS服务失败(最终尝试): 无法连接到TTS服务，请检查Python服务是否启动并在{}端口监听，url={}, type={}, sessionId={}", port, url, type, sessionId, e);
                        return new byte[0];
                    }
                    LOG.warn("调用TTS服务连接异常，第{}次重试：无法连接到TTS服务，请检查Python服务是否启动并在{}端口监听，错误信息: {}", attempt, port, e.toString());
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
        //先将message进行处理，将在两个*符号中间的字段删除
        message = message.replaceAll("\\*.*\\*", "");
        char type = sessionId.charAt(sessionId.length() - 1);
        int port = switch (type) {
            case '0' -> 5000;
            case '1' -> 5001;
            case '2' -> 5002;
            default -> {
                LOG.warn("未识别的type: {}，默认使用5000端口", type);
                yield 5000;
            }
        };

        // 使用IPv4避免localhost解析为IPv6导致连接失败，且与当前api.py保持POST /stream
        String url = "http://127.0.0.1:" + port + "/stream";

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            payload.put("text_language", "zh");
            String json = objectMapper.writeValueAsString(payload);
            byte[] jsonBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_STREAM_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "audio/wav")
                    .header("User-Agent", "Java-TTS-Client/1.0")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes))
                    .build();

            LOG.info("发送TTS流请求: url={}, message={}, sessionId={}, json={}, jsonBytes.length={}", 
                    url, message, sessionId, json, jsonBytes.length);
            
            long backoff = INITIAL_BACKOFF_MS;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    int status = response.statusCode();
                    LOG.info("TTS流请求响应: status={}, attempt={}, url={}, sessionId={}", status, attempt, url, sessionId);
                    
                    if (status == 200) {
                        LOG.info("TTS流请求成功: url={}, sessionId={}", url, sessionId);
                        return response.body();
                    }
                    if (status >= 500 || status == 429) {
                        LOG.warn("TTS流服务响应{}，第{}次重试", status, attempt);
                    } else {
                        LOG.warn("TTS流服务响应非200: {}，不重试", status);
                        return new ByteArrayInputStream(new byte[0]);
                    }
                } catch (java.net.ConnectException e) {
                    if (attempt == MAX_RETRIES) {
                        LOG.error("调用TTS流服务失败(最终尝试): 无法连接到TTS流服务，请检查Python服务是否启动并在{}端口监听，url={}, type={}, sessionId={}", port, url, type, sessionId, e);
                        return new ByteArrayInputStream(new byte[0]);
                    }
                    LOG.warn("调用TTS流服务连接异常，第{}次重试：无法连接到TTS流服务，请检查Python服务是否启动并在{}端口监听，错误信息: {}", attempt, port, e.toString());
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