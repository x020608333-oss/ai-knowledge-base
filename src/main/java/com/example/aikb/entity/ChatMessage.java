package com.example.aikb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息实体：会话中的一条问答记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属会话 */
    private Long sessionId;

    /** 角色：user / assistant */
    private String role;

    /** 内容 */
    private String content;

    /** 引用来源（JSON 字符串，assistant 消息有效） */
    private String references;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
