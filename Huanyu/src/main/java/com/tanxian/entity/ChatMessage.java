package com.tanxian.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_messages")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("message_type")
    private String messageType; // USER, ASSISTANT, SYSTEM

    @TableField("content")
    private String content;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // 自定义构造函数
    public ChatMessage(String sessionId, String messageType, String content) {
        this.sessionId = sessionId;
        this.messageType = messageType;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}