package com.tanxian.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tanxian.entity.ChatMessage;
import com.tanxian.mapper.ChatMessageMapper;
import com.tanxian.service.MyChatMemoryStore;
import dev.langchain4j.data.message.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MyChatMemoryStoreImpl implements MyChatMemoryStore {
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Override
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        log.info("获取会话消息，memoryId: {}", memoryId.toString());
        // 先从Redis中查询
        String json = redisTemplate.opsForValue().get(memoryId.toString());
        if (StringUtils.hasText(json)) {
            log.info("从Redis获取到会话消息");
            return ChatMessageDeserializer.messagesFromJson(json);
        }
        
        log.info("Redis中无数据，从MySQL查询");
        // Redis中没有数据，从MySQL中查询
        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessage::getSessionId, memoryId.toString())
                .orderByAsc(ChatMessage::getCreatedAt);
        List<ChatMessage> dbMessages = chatMessageMapper.selectList(queryWrapper);
        
        if (dbMessages == null || dbMessages.isEmpty()) {
            log.error("MySQL中也没有数据，返回空列表");
            return new ArrayList<>();
        }
        
        log.info("从MySQL获取到{}条消息", dbMessages.size());
        // 将数据库消息转换为langchain4j消息格式
        List<dev.langchain4j.data.message.ChatMessage> langchainMessages = convertToLangchainMessages(dbMessages);
        
        // 将查询结果存入Redis，设置1天过期时间
        String newJson = ChatMessageSerializer.messagesToJson(langchainMessages);
        redisTemplate.opsForValue().set(memoryId.toString(), newJson, Duration.ofDays(1));
        log.info("已将查询结果缓存到Redis，过期时间1天");
        
        return langchainMessages;
    }

    @Override
    public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        log.info("更新会话消息，memoryId: {}, 消息数量: {}", memoryId, messages.size());
        
        // 更新Redis，设置1天过期时间
        String json = ChatMessageSerializer.messagesToJson(messages);
        redisTemplate.opsForValue().set(memoryId.toString(), json, Duration.ofDays(1));
        log.info("已更新Redis缓存");
        
        // 更新MySQL
        // 先删除该会话的所有消息
        LambdaQueryWrapper<ChatMessage> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ChatMessage::getSessionId, memoryId.toString());
        chatMessageMapper.delete(deleteWrapper);
        log.info("已删除MySQL中的旧消息");
        
        // 再插入新的消息
        List<ChatMessage> dbMessages = convertToDbMessages(memoryId.toString(), messages);
        log.info("准备插入{}条新消息到MySQL", dbMessages.size());
        
        for (ChatMessage message : dbMessages) {
            chatMessageMapper.insert(message);
        }
        log.info("MySQL消息更新完成");
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // 删除Redis中的数据
        redisTemplate.delete(memoryId.toString());
        
        // 删除MySQL中的数据
        LambdaQueryWrapper<ChatMessage> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ChatMessage::getSessionId, memoryId.toString());
        chatMessageMapper.delete(deleteWrapper);
    }

    /**
     * 获取可序列化的消息列表，用于API返回
     * @param sessionId 会话ID
     * @return 可序列化的消息列表
     */
    @Override
    public List<SerializableChatMessage> getSerializableMessages(String sessionId) {
        List<dev.langchain4j.data.message.ChatMessage> messages = getMessages(sessionId);
        return messages.stream().map(message -> {
            if (message instanceof SystemMessage) {
                return new SerializableChatMessage("SYSTEM", ((SystemMessage) message).text());
            } else if (message instanceof UserMessage) {
                return new SerializableChatMessage("USER", ((UserMessage) message).singleText());
            } else if (message instanceof AiMessage) {
                return new SerializableChatMessage("AI", ((AiMessage) message).text());
            } else {
                return new SerializableChatMessage("UNKNOWN", message.toString());
            }
        }).collect(Collectors.toList());
    }
    
    /**
     * 将数据库消息转换为langchain4j消息格式
     */
    private List<dev.langchain4j.data.message.ChatMessage> convertToLangchainMessages(List<ChatMessage> dbMessages) {
        return dbMessages.stream().map(dbMessage -> switch (dbMessage.getMessageType()) {
            case "ASSISTANT" -> new AiMessage(dbMessage.getContent());
            case "SYSTEM" -> new SystemMessage(dbMessage.getContent());
            default -> new UserMessage(dbMessage.getContent());
        }).collect(Collectors.toList());
    }
    
    /**
     * 将langchain4j消息转换为数据库消息格式
     */
    private List<ChatMessage> convertToDbMessages(String sessionId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        return messages.stream().map(message -> {
            ChatMessage dbMessage = new ChatMessage();
            dbMessage.setSessionId(sessionId);
            
            // 根据不同消息类型获取内容
            if (message instanceof UserMessage) {
                String content = ((UserMessage) message).singleText();
                // 确保内容不为空
                dbMessage.setContent(StringUtils.hasText(content) ? content : "空消息");
                dbMessage.setMessageType("USER");
            } else if (message instanceof AiMessage) {
                String content = ((AiMessage) message).text();
                dbMessage.setContent(StringUtils.hasText(content) ? content : "空回复");
                dbMessage.setMessageType("ASSISTANT");
            } else if (message instanceof SystemMessage) {
                String content = ((SystemMessage) message).text();
                dbMessage.setContent(StringUtils.hasText(content) ? content : "系统消息");
                dbMessage.setMessageType("SYSTEM");
            } else {
                // 对于未知类型的消息，确保内容不为空
                String content = message.toString();
                dbMessage.setContent(StringUtils.hasText(content) ? content : "未知类型消息");
                dbMessage.setMessageType("USER");
            }
            
            dbMessage.setCreatedAt(LocalDateTime.now());
            return dbMessage;
        }).collect(Collectors.toList());
    }
    
    /**
     * 可序列化的聊天消息DTO类
     */
    public static class SerializableChatMessage {
        private String type;
        private String content;
        
        public SerializableChatMessage(String type, String content) {
            this.type = type;
            this.content = content;
        }
        
        public String getType() {
            return type;
        }
        
        public String getContent() {
            return content;
        }
    }
}