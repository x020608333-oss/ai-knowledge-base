package com.example.aikb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 自定义参数绑定（application.yml 中 app.rag.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /** 向量检索召回条数 */
    private Integer topK = 5;

    /** 相似度阈值 */
    private Double similarityThreshold = 0.5;

    /** 会话记忆窗口大小 */
    private Integer chatMemoryWindow = 10;

    /** 拼接进 Prompt 的最大上下文字符数 */
    private Integer maxContextChars = 4000;

    /** SimpleVectorStore 本地持久化文件路径 */
    private String vectorStorePath = "data/vector-store.json";
}
