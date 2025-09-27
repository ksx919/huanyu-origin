package com.tanxian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tanxian.entity.ChatMessage;
import com.tanxian.resp.ChatMessageResp;
import com.tanxian.service.impl.MyChatMemoryStoreImpl;
import io.lettuce.core.dynamic.annotation.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    @Select("SELECT * FROM chat_messages WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<ChatMessageResp> findBySessionId(@Param("sessionId") String sessionId);
}
