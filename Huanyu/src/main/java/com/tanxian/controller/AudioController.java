package com.tanxian.controller;

import com.tanxian.demo.SpeechTranscriberDemo;
import com.tanxian.service.impl.SpeechTranscriberTool;
import com.tanxian.common.CommonResp;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
@RequestMapping("/audio")
public class AudioController {
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
            SpeechTranscriberDemo speechTranscriberDemo = new SpeechTranscriberDemo();
            String result = speechTranscriberDemo.process(file.toString());

            // 返回统一响应结构
            return CommonResp.success(result == null ? "" : result);
        } catch (Exception e) {
            e.printStackTrace();
            return CommonResp.error("上传失败: " + e.getMessage());
        }
    }
}