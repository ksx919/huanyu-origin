package com.tanxian.controller;

import com.tanxian.handler.BatchSpeechTranscriber;
import com.tanxian.common.CommonResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
@RequestMapping("/audio")
public class AudioController {

    @Autowired
    BatchSpeechTranscriber batchSpeechTranscriber;

    // 接收原始二进制流（Content-Type 需与前端一致，如 audio/wav）
    @PostMapping(value = "/upload-raw", consumes = {"audio/wav", MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public CommonResp<String> uploadRawAudio(@RequestBody byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            return CommonResp.error("上传失败：音频数据为空");
        }

        try {
            // 保存到临时目录，避免绝对路径权限问题
            Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "huanyu", "audio");
            Files.createDirectories(dir);
            Path file = dir.resolve("audio_" + System.currentTimeMillis() + ".wav");
            Files.write(file, audioBytes);
            // 调用语音识别
            String result = batchSpeechTranscriber.process(file.toString());

            // 返回统一响应结构
            return CommonResp.success(result == null ? "" : result);
        } catch (Exception e) {
            e.printStackTrace();
            return CommonResp.error("上传失败: " + e.getMessage());
        }
    }
}