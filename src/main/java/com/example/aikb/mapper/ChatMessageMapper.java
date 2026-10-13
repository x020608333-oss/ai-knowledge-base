package com.example.aikb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aikb.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询某会话最近 N 条消息（按时间正序，用于构建多轮上下文）。
     */
    @Select("""
            SELECT * FROM chat_message
            WHERE session_id = #{sessionId}
            ORDER BY create_time DESC
            LIMIT #{limit}
            """)
    List<ChatMessage> findRecentMessages(Long sessionId, Integer limit);
}
