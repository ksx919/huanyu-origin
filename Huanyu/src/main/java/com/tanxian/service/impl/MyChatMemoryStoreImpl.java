package com.tanxian.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tanxian.common.LoginUserContext;
import com.tanxian.entity.ChatMessage;
import com.tanxian.entity.ChatSession;
import com.tanxian.exception.BusinessException;
import com.tanxian.exception.BusinessExceptionEnum;
import com.tanxian.mapper.ChatMessageMapper;
import com.tanxian.mapper.ChatSessionMapper;
import com.tanxian.service.MessageTurnToAiVoiceTool;
import com.tanxian.service.MyChatMemoryStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MyChatMemoryStoreImpl implements MyChatMemoryStore {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    MessageTurnToAiVoiceTool messageTurnToAiVoiceTool;

    // 系统消息模板缓存（从本地文件加载）
    private final Map<String, String> systemMessageCache = new ConcurrentHashMap<>();

    // 标记是否已初始化
    private volatile boolean initialized = false;

    // 空构造函数
    public MyChatMemoryStoreImpl() {
        log.info("MyChatMemoryStoreImpl 构造函数执行");
    }

    /**
     * 延迟初始化系统消息缓存
     */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    try {
                        initSystemMessageCache();
                        initialized = true;
                        log.info("系统消息缓存初始化完成");
                    } catch (Exception e) {
                        log.error("延迟初始化系统消息缓存失败", e);
                        throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED);
                    }
                }
            }
        }
    }

    /**
     * 加载所有prompt文件到内存缓存
     */
    private void initSystemMessageCache() {
        log.info("开始加载系统消息模板到内存缓存...");

        try {
            // 加载宵宫模板
            loadPromptTemplate("yoimiya", "prompt/Yoimiya.md");

            // 加载温迪模板
            loadPromptTemplate("venti", "prompt/Venti.md");

            // 加载胡桃模板
            loadPromptTemplate("hutao", "prompt/HuTao.md");

            log.info("系统消息模板加载完成，共加载 {} 个模板", systemMessageCache.size());

        } catch (Exception e) {
            log.error("加载系统消息模板失败", e);
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED);
        }
    }

    /**
     * 根据sessionId获取系统消息
     */
    private SystemMessage getSystemMessageBySessionId(String sessionId) {
        ensureInitialized(); // 确保已初始化

        try {
            String templateKey = determineTemplateKeyFromSessionId(sessionId);
            String content = systemMessageCache.get(templateKey);

            if (StringUtils.hasText(content)) {
                return SystemMessage.from(content);
            }

            // 没找到模板时抛出异常
            log.error("未找到系统消息模板，templateKey: {}", templateKey);
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_NOT_FOUND);

        } catch (BusinessException e) {
            throw e; // 重新抛出业务异常
        } catch (Exception e) {
            log.error("获取系统消息失败，sessionId: {}", sessionId, e);
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED);
        }
    }

    /**
     * 从本地资源文件加载prompt模板
     */
    private void loadPromptTemplate(String templateKey, String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                    String content = reader.lines().collect(Collectors.joining("\n"));
                    if (StringUtils.hasText(content.trim())) {
                        systemMessageCache.put(templateKey, content);
                        log.info("已加载模板: {} -> {}", templateKey, resourcePath);
                    } else {
                        log.error("模板文件内容为空: {}", resourcePath);
                        throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED);
                    }
                }
            } else {
                log.error("模板文件不存在: {}", resourcePath);
                throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_NOT_FOUND);
            }
        } catch (IOException e) {
            log.error("加载模板文件失败: {}", resourcePath, e);
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED);
        }
    }

    /**
     * 根据角色类型获取模板key
     */
    private String getTemplateKeyByCharacterType(Short characterType) {
        if (characterType == null) {
            log.error("角色类型为空");
            throw new BusinessException(BusinessExceptionEnum.UNSUPPORTED_CHARACTER_TYPE);
        }

        return switch (characterType) {
            case 0 -> "yoimiya";
            case 1 -> "venti";
            case 2 -> "hutao";
            default -> {
                log.error("不支持的角色类型: {}", characterType);
                throw new BusinessException(BusinessExceptionEnum.UNSUPPORTED_CHARACTER_TYPE);
            }
        };
    }

    /**
     * 根据sessionId确定模板key
     */
    private String determineTemplateKeyFromSessionId(String sessionId) {
        try {
            LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ChatSession::getSessionId, sessionId);
            ChatSession chatSession = chatSessionMapper.selectOne(queryWrapper);

            if (chatSession != null) {
                Short characterType = chatSession.getCharacterType();
                return getTemplateKeyByCharacterType(characterType);
            }

            log.error("未找到sessionId对应的ChatSession记录: {}", sessionId);
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_NOT_FOUND);

        } catch (BusinessException e) {
            throw e; // 重新抛出业务异常
        } catch (Exception e) {
            log.error("查询ChatSession失败，sessionId: {}", sessionId, e);
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED);
        }
    }

    /**
     * 生成Redis键名
     */
    private String getRedisKey(String sessionId) {
        return "chat:session:" + sessionId;
    }

    @Override
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        ensureInitialized();

        String sessionId = memoryId.toString();
        log.info("获取会话消息，sessionId: {}", sessionId);

        try {
            String redisKey = getRedisKey(sessionId);
            String json = redisTemplate.opsForValue().get(redisKey);
            if (StringUtils.hasText(json)) {
                log.info("从Redis获取到会话消息");
                List<dev.langchain4j.data.message.ChatMessage> cachedMessages = ChatMessageDeserializer.messagesFromJson(json);
                return addSystemMessageToMessages(cachedMessages, sessionId);
            }

            log.info("Redis中无数据，从MySQL查询");
            LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ChatMessage::getSessionId, sessionId)
                    .ne(ChatMessage::getMessageType, "SYSTEM")
                    .orderByAsc(ChatMessage::getCreatedAt); // 按时间升序获取所有消息
            List<ChatMessage> dbMessages = chatMessageMapper.selectList(queryWrapper);

            if (dbMessages == null || dbMessages.isEmpty()) {
                log.info("MySQL中也没有数据，返回只包含系统消息的列表");
                return addSystemMessageToMessages(new ArrayList<>(), sessionId);
            }

            log.info("从MySQL获取到{}条消息", dbMessages.size());
            List<dev.langchain4j.data.message.ChatMessage> nonSystemMessages = convertToLangchainMessages(dbMessages);
            String newJson = ChatMessageSerializer.messagesToJson(nonSystemMessages);
            redisTemplate.opsForValue().set(redisKey, newJson, Duration.ofDays(1));
            log.info("已将查询结果缓存到Redis，过期时间1天");

            return addSystemMessageToMessages(nonSystemMessages, sessionId);

        } catch (Exception e) {
            log.error("获取会话消息失败，sessionId: {}", sessionId, e);
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        ensureInitialized();

        String sessionId = memoryId.toString();
        log.info("更新会话消息，sessionId: {}, 消息数量: {}", sessionId, messages.size());

        try {
            List<dev.langchain4j.data.message.ChatMessage> nonSystemMessages = messages.stream()
                    .filter(msg -> !(msg instanceof SystemMessage))
                    .collect(Collectors.toList());

            // 更新Redis缓存（仅存储内存窗口内的消息）
            String redisKey = getRedisKey(sessionId);
            String json = ChatMessageSerializer.messagesToJson(nonSystemMessages);
            redisTemplate.opsForValue().set(redisKey, json, Duration.ofDays(1));
            log.info("已更新Redis缓存");
            
            // 完整存储所有消息到数据库（不受内存窗口限制）
            storeAllMessagesToDatabase(sessionId, nonSystemMessages);
            
        } catch (Exception e) {
            log.error("更新会话消息失败，sessionId: {}", sessionId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        String redisKey = getRedisKey(sessionId);

        try {
            redisTemplate.delete(redisKey);
            log.info("已删除Redis缓存: {}", redisKey);

            LambdaQueryWrapper<ChatMessage> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(ChatMessage::getSessionId, sessionId)
                    .ne(ChatMessage::getMessageType, "SYSTEM");
            chatMessageMapper.delete(deleteWrapper);
            log.info("已删除MySQL中的非系统消息: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("删除会话消息失败，sessionId: {}", sessionId, e);
        }
    }

    /**
     * 动态添加SYSTEM消息到消息列表前面
     */
    private List<dev.langchain4j.data.message.ChatMessage> addSystemMessageToMessages(
            List<dev.langchain4j.data.message.ChatMessage> messages, String sessionId) {
        List<dev.langchain4j.data.message.ChatMessage> result = new ArrayList<>();

        SystemMessage systemMessage = getSystemMessageBySessionId(sessionId);
        result.add(systemMessage);
        result.addAll(messages);

        return result;
    }

    /**
     * 将数据库消息转换为langchain4j消息格式
     */
    private List<dev.langchain4j.data.message.ChatMessage> convertToLangchainMessages(List<ChatMessage> dbMessages) {
        List<dev.langchain4j.data.message.ChatMessage> result = new ArrayList<>();

        for (ChatMessage dbMessage : dbMessages) {
            try {
                if (!"SYSTEM".equals(dbMessage.getMessageType())) {
                    dev.langchain4j.data.message.ChatMessage message = switch (dbMessage.getMessageType()) {
                        case "ASSISTANT" -> new AiMessage(dbMessage.getContent());
                        case "USER" -> new UserMessage(dbMessage.getContent());
                        default -> {
                            log.warn("未知消息类型: {}, 当作USER消息处理", dbMessage.getMessageType());
                            yield new UserMessage(dbMessage.getContent());
                        }
                    };
                    result.add(message);
                }
            } catch (Exception e) {
                log.error("转换消息失败，跳过该消息: {}", dbMessage, e);
            }
        }

        return result;
    }

    /**
     * 获取可序列化的消息列表，用于API返回
     */
    public List<SerializableChatMessage> getSerializableMessages(String sessionId) {
        try {
            // 复用getMessages方法的逻辑获取消息
            List<dev.langchain4j.data.message.ChatMessage> messages = getMessages(sessionId);
            
            // 过滤掉系统消息，只返回用户和AI的消息
            return messages.stream()
                    .filter(message -> !(message instanceof SystemMessage))
                    .map(message -> {
                        try {
                            if (message instanceof UserMessage) {
                                return new SerializableChatMessage("USER", ((UserMessage) message).singleText());
                            } else if (message instanceof AiMessage) {
                                return new SerializableChatMessage("AI", ((AiMessage) message).text());
                            } else {
                                return new SerializableChatMessage("UNKNOWN", message.toString());
                            }
                        } catch (Exception e) {
                            log.error("序列化消息失败: {}", message, e);
                            return new SerializableChatMessage("ERROR", "消息解析失败");
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取可序列化消息列表失败，sessionId: {}", sessionId, e);
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED);
        }
    }

    /**
     * 完整存储所有消息到数据库，不受内存窗口限制
     * 这个方法会存储会话中的每一条消息，而不仅仅是内存窗口中的消息
     */
    private void storeAllMessagesToDatabase(String sessionId, List<dev.langchain4j.data.message.ChatMessage> currentWindowMessages) {
        try {
            // 获取数据库中已存在的消息数量
            LambdaQueryWrapper<ChatMessage> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(ChatMessage::getSessionId, sessionId)
                    .ne(ChatMessage::getMessageType, "SYSTEM");
            Long existingCount = chatMessageMapper.selectCount(countWrapper);
            
            log.info("会话 {} 数据库中已有 {} 条消息，当前内存窗口有 {} 条消息", sessionId, existingCount, currentWindowMessages.size());
            
            // 检查是否有新消息需要存储
            // 由于LangChain4j的内存窗口限制，我们需要检测新增的消息
            if (!currentWindowMessages.isEmpty()) {
                // 获取当前窗口中的最后一条消息
                dev.langchain4j.data.message.ChatMessage lastMessage = currentWindowMessages.get(currentWindowMessages.size() - 1);
                
                // 检查这条消息是否已经存在于数据库中
                if (!isMessageExistsInDatabase(sessionId, lastMessage)) {
                    // 如果最后一条消息不存在，说明有新消息需要存储
                    ChatMessage dbMessage = convertSingleMessageToDb(sessionId, lastMessage);
                    if (dbMessage != null) {
                        chatMessageMapper.insert(dbMessage);
                        log.info("成功存储新消息到数据库: {} - {}", dbMessage.getMessageType(), 
                                dbMessage.getContent().length() > 50 ? 
                                dbMessage.getContent().substring(0, 50) + "..." : 
                                dbMessage.getContent());
                        // if(dbMessage.getMessageType().equals("ASSISTANT")) {
                        //     messageTurnToAiVoiceTool.turnToAiVoice(dbMessage.getContent(),dbMessage.getSessionId());
                        // }
                    }
                } else {
                    log.debug("消息已存在于数据库中，无需重复存储");
                }
            }
            
        } catch (Exception e) {
            log.error("完整存储消息到数据库失败，sessionId: {}", sessionId, e);
        }
    }
    
    /**
     * 检查消息是否已存在于数据库中
     */
    private boolean isMessageExistsInDatabase(String sessionId, dev.langchain4j.data.message.ChatMessage message) {
        try {
            // 获取数据库中最后几条消息进行比较
            LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ChatMessage::getSessionId, sessionId)
                    .ne(ChatMessage::getMessageType, "SYSTEM")
                    .orderByDesc(ChatMessage::getCreatedAt)
                    .last("LIMIT 5"); // 只检查最后5条消息
            
            List<ChatMessage> recentMessages = chatMessageMapper.selectList(queryWrapper);
            
            String messageContent = getMessageContent(message);
            String messageType = getMessageType(message);
            
            // 检查是否有相同内容和类型的消息
            return recentMessages.stream().anyMatch(dbMsg -> 
                messageType.equals(dbMsg.getMessageType()) && 
                messageContent.equals(dbMsg.getContent())
            );
            
        } catch (Exception e) {
            log.error("检查消息是否存在时发生错误", e);
            return false;
        }
    }
    
    /**
     * 将单条LangChain4j消息转换为数据库消息
     */
    private ChatMessage convertSingleMessageToDb(String sessionId, dev.langchain4j.data.message.ChatMessage message) {
        try {
            ChatMessage dbMessage = new ChatMessage();
            dbMessage.setSessionId(sessionId);
            
            if (message instanceof UserMessage) {
                String content = ((UserMessage) message).singleText();
                dbMessage.setContent(StringUtils.hasText(content) ? content : "空消息");
                dbMessage.setMessageType("USER");
            } else if (message instanceof AiMessage) {
                String content = ((AiMessage) message).text();
                dbMessage.setContent(StringUtils.hasText(content) ? content : "空回复");
                dbMessage.setMessageType("ASSISTANT");
            } else {
                String content = message.toString();
                dbMessage.setContent(StringUtils.hasText(content) ? content : "未知类型消息");
                dbMessage.setMessageType("USER");
            }
            
            dbMessage.setCreatedAt(LocalDateTime.now());
            return dbMessage;
            
        } catch (Exception e) {
            log.error("转换单条消息失败: {}", message, e);
            return null;
        }
    }
    
    /**
     * 获取消息内容
     */
    private String getMessageContent(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof UserMessage) {
            return ((UserMessage) message).singleText();
        } else if (message instanceof AiMessage) {
            return ((AiMessage) message).text();
        } else {
            return message.toString();
        }
    }
    
    /**
     * 获取消息类型
     */
    private String getMessageType(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof UserMessage) {
            return "USER";
        } else if (message instanceof AiMessage) {
            return "ASSISTANT";
        } else {
            return "USER";
        }
    }
//    private void insertNewMessagesOnly(String sessionId, List<dev.langchain4j.data.message.ChatMessage> newMessages) {
//        try {
//            // 获取数据库中已存在的消息数量
//            LambdaQueryWrapper<ChatMessage> countWrapper = new LambdaQueryWrapper<>();
//            countWrapper.eq(ChatMessage::getSessionId, sessionId)
//                    .ne(ChatMessage::getMessageType, "SYSTEM");
//            Long existingCount = chatMessageMapper.selectCount(countWrapper);
//
//            log.info("会话 {} 数据库中已有 {} 条消息，新消息列表有 {} 条", sessionId, existingCount, newMessages.size());
//
//            // 如果新消息数量大于已存在的消息数量，则插入差异部分
//            if (newMessages.size() > existingCount) {
//                List<dev.langchain4j.data.message.ChatMessage> messagesToInsert =
//                    newMessages.subList(existingCount.intValue(), newMessages.size());
//
//                List<ChatMessage> dbMessages = convertToDbMessages(sessionId, messagesToInsert);
//
//                if (!dbMessages.isEmpty()) {
//                    // 逐条插入新消息（MyBatis-Plus BaseMapper没有批量插入方法）
//                    for (ChatMessage dbMessage : dbMessages) {
//                        chatMessageMapper.insert(dbMessage);
//                    }
//                    log.info("成功插入 {} 条新消息到数据库", dbMessages.size());
//                } else {
//                    log.info("没有新消息需要插入");
//                }
//            } else {
//                log.info("消息数量未增加，无需插入新消息");
//            }
//
//        } catch (Exception e) {
//            log.error("增量插入消息失败，sessionId: {}", sessionId, e);
//        }
//    }

//    /**
//     * 将langchain4j消息转换为数据库消息格式
//     */
//    private List<ChatMessage> convertToDbMessages(String sessionId, List<dev.langchain4j.data.message.ChatMessage> messages) {
//        return messages.stream()
//                .filter(message -> !(message instanceof SystemMessage))
//                .map(message -> {
//                    try {
//                        ChatMessage dbMessage = new ChatMessage();
//                        dbMessage.setSessionId(sessionId);
//
//                        if (message instanceof UserMessage) {
//                            String content = ((UserMessage) message).singleText();
//                            dbMessage.setContent(StringUtils.hasText(content) ? content : "空消息");
//                            dbMessage.setMessageType("USER");
//                        } else if (message instanceof AiMessage) {
//                            String content = ((AiMessage) message).text();
//                            dbMessage.setContent(StringUtils.hasText(content) ? content : "空回复");
//                            dbMessage.setMessageType("ASSISTANT");
//                        } else {
//                            String content = message.toString();
//                            dbMessage.setContent(StringUtils.hasText(content) ? content : "未知类型消息");
//                            dbMessage.setMessageType("USER");
//                        }
//
//                        dbMessage.setCreatedAt(LocalDateTime.now());
//                        return dbMessage;
//                    } catch (Exception e) {
//                        log.error("转换数据库消息失败: {}", message, e);
//                        return null;
//                    }
//                })
//                .filter(Objects::nonNull)
//                .collect(Collectors.toList());
//    }

    /**
     * 记录会话信息
     */
    @Override
    public void recordChatSession(String sessionId, Short characterType) {
        try {
            LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ChatSession::getSessionId, sessionId);
            ChatSession existingSession = chatSessionMapper.selectOne(queryWrapper);

            if (existingSession == null) {
                ChatSession chatSession = new ChatSession();
                chatSession.setSessionId(sessionId);
                chatSession.setUserId(LoginUserContext.getId());
                chatSession.setCharacterType(characterType);
                chatSession.setCharacterName(getCharacterNameByType(characterType));
                chatSession.setCreatedAt(LocalDateTime.now());
                chatSession.setUpdatedAt(LocalDateTime.now());

                chatSessionMapper.insert(chatSession);
                log.info("已记录新会话: sessionId={}, characterType={}, characterName={}",
                        sessionId, characterType, getCharacterNameByType(characterType));
            } else {
                if (!characterType.equals(existingSession.getCharacterType())) {
                    existingSession.setCharacterType(characterType);
                    existingSession.setCharacterName(getCharacterNameByType(characterType));
                    existingSession.setUpdatedAt(LocalDateTime.now());

                    chatSessionMapper.updateById(existingSession);
                    log.info("已更新会话角色: sessionId={}, characterType={}, characterName={}",
                            sessionId, characterType, getCharacterNameByType(characterType));
                } else {
                    log.debug("会话已存在且角色类型相同，无需更新: sessionId={}", sessionId);
                }
            }
        } catch (Exception e) {
            log.error("记录会话信息失败: sessionId={}, characterType={}", sessionId, characterType, e);
        }
    }

    /**
     * 根据角色类型获取角色名称
     */
    private String getCharacterNameByType(Short characterType) {
        return switch (characterType) {
            case 0 -> "宵宫";
            case 1 -> "温迪";
            case 2 -> "胡桃";
            default -> {
                log.warn("不支持的角色类型: {}, 使用默认名称", characterType);
                yield "默认角色";
            }
        };
    }

    /**
     * 可序列化的聊天消息类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerializableChatMessage {
        private String type;
        private String content;
    }

}