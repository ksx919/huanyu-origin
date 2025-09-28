package com.tanxian.service.impl;

import com.tanxian.exception.BusinessException;
import com.tanxian.exception.BusinessExceptionEnum;
import com.tanxian.common.LoginUserContext;
import com.tanxian.resp.LoginResp;
import com.tanxian.service.AiChatService;
import com.tanxian.service.MyChatMemoryStore;
import com.tanxian.service.MessageTurnToAiVoiceTool;
import com.tanxian.util.QiniuUploadUtil;
import com.tanxian.service.ai.HuTaoService;
import com.tanxian.service.ai.VentiService;
import com.tanxian.service.ai.YoimiyaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class AiChatServiceImpl implements AiChatService {
    @Autowired
    private YoimiyaService yoimiyaService;

    @Autowired
    private VentiService ventiService;

    @Autowired
    private HuTaoService huTaoService;

    @Autowired
    private MyChatMemoryStore myChatMemoryStore;

    @Autowired
    private MessageTurnToAiVoiceTool messageTurnToAiVoiceTool;

    @Autowired
    private QiniuUploadUtil qiniuUploadUtil;

    @Override
    public Flux<String> chat(String sessionId, String message, short type) {
        // 记录会话信息（如果是新会话）
        myChatMemoryStore.recordChatSession(sessionId, type);
        StringBuilder fullText = new StringBuilder();

        Flux<String> flux = switch (type) {
            case 0 -> yoimiyaService.chat(sessionId, message);
            case 1 -> ventiService.chat(sessionId, message);
            case 2 -> huTaoService.chat(sessionId, message);
            default -> throw new BusinessException(BusinessExceptionEnum.CHAT_TYPE_ERROR);
        };

        // 边流式边累计完整文本，并在结束后异步生成并上传音频
        return flux
                .doOnNext(fullText::append)
                .doOnComplete(() -> {
                    String aiText = fullText.toString();
                    if (aiText == null || aiText.isBlank()) return;
                    // 异步执行，避免阻塞文本流
                    Schedulers.boundedElastic().schedule(() -> {
                        try {
                            // 生成固定文件键：huanyu/audio/<type>-<sha1(text)>.wav
                            String sha1 = sha1Hex(aiText.trim());
                            String fileKey = "huanyu/audio/" + type + "-" + sha1 + ".wav";
                            boolean existsBefore = qiniuUploadUtil.exists(fileKey);
                            System.out.println("[AiChatService] 准备上传音频 fileKey=" + fileKey + ", existsBefore=" + existsBefore);
                            if (!existsBefore) {
                                byte[] wav = messageTurnToAiVoiceTool.turnToAiVoice(aiText, sessionId);
                                System.out.println("[AiChatService] TTS生成字节大小=" + (wav == null ? 0 : wav.length));
                                if (wav != null && wav.length > 0) {
                                    try {
                                        qiniuUploadUtil.uploadAudioBytesWithKey(wav, fileKey);
                                        boolean existsAfter = qiniuUploadUtil.exists(fileKey);
                                        System.out.println("[AiChatService] 上传完成 existsAfter=" + existsAfter + ", fileKey=" + fileKey);
                                        // 可选：设置几天后自动删除
                                        qiniuUploadUtil.setDeleteAfterDays(fileKey, 1);
                                    } catch (Exception ue) {
                                        System.err.println("[AiChatService] 七牛上传失败: " + ue.getMessage());
                                        ue.printStackTrace();
                                    }
                                } else {
                                    System.err.println("[AiChatService] TTS生成音频为空，跳过上传 fileKey=" + fileKey);
                                }
                            } else {
                                System.out.println("[AiChatService] 音频已存在，跳过上传 fileKey=" + fileKey);
                            }
                        } catch (Exception e) {
                            // 仅记录，不影响聊天流
                            e.printStackTrace();
                        }
                    });
                });
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
