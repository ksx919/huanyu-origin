package com.tanxian.service;

import org.springframework.web.multipart.MultipartFile;

public interface AiAudioService {
    void upload(MultipartFile file);
}
