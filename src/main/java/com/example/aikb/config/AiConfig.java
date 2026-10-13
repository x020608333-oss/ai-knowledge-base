package com.example.aikb.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 核心 Bean 配置。
 *
 * 统一构建 ChatClient，注入默认 System Prompt，作为 RAG 问答的统一入口。
 *
 * 说明：
 *   - EmbeddingModel 由 spring-ai-starter-model-ollama 自动装配直接提供，无需在此声明；
 *   - VectorStore 由 {@link VectorStoreConfig} 构建（默认 SimpleVectorStore 本地向量库），
 *     故此处不再定义同名 Bean，避免冲突。
 */
@Configuration
public class AiConfig {

    /**
     * RAG 系统提示词：约束模型只能基于给定上下文回答，杜绝幻觉。
     */
    public static final String RAG_SYSTEM_PROMPT = """
            你是一个专业的企业知识库问答助手。请严格遵守以下规则：
            1. 只能基于下方【参考资料】中的内容回答用户问题，禁止编造参考资料中不存在的信息。
            2. 如果参考资料中没有能回答该问题的内容，请明确回复："根据现有知识库，我无法找到相关信息。"
            3. 回答需准确、简洁、条理清晰，必要时使用列表。
            4. 在回答末尾，标注你引用到的资料编号，格式如 [1][2]。

            【参考资料】
            {context}
            """;

    /**
     * 构建 ChatClient，注入默认系统角色。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("你是 AI 智能知识库问答助手，基于提供的参考资料为用户答疑解惑。")
                .build();
    }
}

