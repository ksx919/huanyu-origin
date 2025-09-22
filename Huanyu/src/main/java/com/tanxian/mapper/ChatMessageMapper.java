package com.tanxian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tanxian.entity.ChatMessage;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    /**
     * 根据会话ID查询消息列表，按创建时间升序
     */
    List<ChatMessage> selectBySessionIdOrderByCreatedAt(String sessionId);

    /**
     * 根据会话ID查询最近N条消息
     */
    List<ChatMessage> selectRecentMessagesBySessionId(String sessionId, int limit);
}
