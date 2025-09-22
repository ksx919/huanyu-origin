package com.tanxian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tanxian.entity.ChatMessage;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
