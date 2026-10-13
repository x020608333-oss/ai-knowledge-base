package com.example.aikb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 问答请求体
 */
@Data
public class ChatRequest {

    /** 会话 ID（首次可为空，服务端自动创建） */
    private String sessionId;

    /** 用户问题 */
    @NotBlank(message = "问题不能为空")
    private String question;
}
