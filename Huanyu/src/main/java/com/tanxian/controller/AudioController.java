package com.tanxian.controller;

import com.tanxian.demo.SpeechTranscriberDemo;
import com.tanxian.service.impl.SpeechTranscriberTool;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/audio")
public class AudioController {
    // 接收原始二进制流（Content-Type 需与前端一致，如 audio/wav）
    @PostMapping("/upload-raw")
    public String uploadRawAudio(@RequestBody byte[] audioBytes) {

        if (audioBytes.length == 0) {
            return "上传失败：音频数据为空";
        }

        try {
            // 处理二进制流（如保存到内存、转码、调用语音识别 API）===

            // 示例：保存到文件（可选）
            Files.write(Paths.get("/uploads/audio.pcm"), audioBytes);
            SpeechTranscriberDemo speechTranscriberDemo = new SpeechTranscriberDemo();
            String result = speechTranscriberDemo.process("/uploads/audio.pcm");
            return "上传成功！";

        } catch (Exception e) {
            e.printStackTrace();
            return "上传失败: " + e.getMessage();
        }
    }
}