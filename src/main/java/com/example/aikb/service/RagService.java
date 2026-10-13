package com.example.aikb.service;

import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG 编排核心服务（读取 + 生成侧）。
 */
public interface RagService {

    /**
     * 基于知识库回答问题（SSE 流式输出）。
     *
     * @param sessionId  会话 ID，可为空
     * @param question   用户问题
     * @return 流式回答文本（最终以特殊标记携带引用来源）
     */
    Flux<String> chat(String sessionId, String question);

    /**
     * 仅做检索，返回召回的上下文片段（便于测试与调试）。
     */
    List<com.example.aikb.dto.RetrievedChunk> retrieve(String question, int topK);
}
