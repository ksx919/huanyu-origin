package com.tanxian.service.impl;

import com.tanxian.service.AiAudioService;
import com.tanxian.util.QiniuUploadUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiAudioServiceImpl implements AiAudioService {
    @Autowired
    private QiniuUploadUtil qiniuUploadUtil;

    @Override
    public void upload(MultipartFile file) {
        qiniuUploadUtil.uploadAudio(file);
    }
}
