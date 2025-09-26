package com.tanxian.service.impl;

import com.tanxian.service.MessageTurnToAiVoiceTool;
import org.springframework.stereotype.Service;

@Service
public class MessageTurnToAiVoiceToolImpl implements MessageTurnToAiVoiceTool {

    @Override
    public byte[] turnToAiVoice(String message,String sessionId) {
        //接受ai的文字回复，返回音频的byte[]
        System.out.println("已经接受到文字回复，正在将文字回复转为ai语音");
        return new byte[0];
    }
}
