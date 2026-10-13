package com.example.aikb.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 向量库配置：默认使用 Spring AI 内置的 {@link SimpleVectorStore}。
 *
 * <p>设计要点（简历可讲的可插拔架构）：</p>
 * <ul>
 *   <li><b>零外部依赖</b>：无需 PostgreSQL / pgVector / Docker，开箱即用；</li>
 *   <li><b>本地持久化</b>：向量序列化为 JSON 文件（默认 {@code data/vector-store.json}），
 *       启动时自动加载、停机时自动保存，重启后向量不丢失；</li>
 *   <li><b>可插拔</b>：业务层只依赖 {@link VectorStore} 接口，切换到 pgVector / Qdrant / Milvus
 *       只需替换此处 Bean 的实现（配合 application-docker.yml），上层 Service 无感知。</li>
 * </ul>
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    /** 向量库本地持久化文件路径 */
    @Value("${app.rag.vector-store-path:data/vector-store.json}")
    private String vectorStorePath;

    /** 持有构建后的实例，供停机时持久化 */
    private SimpleVectorStore vectorStoreInstance;

    /**
     * 构建 SimpleVectorStore，并在启动时加载本地向量文件（若存在），实现重启后数据不丢。
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        this.vectorStoreInstance = SimpleVectorStore.builder(embeddingModel).build();

        File file = new File(vectorStorePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (file.exists() && file.length() > 0) {
            try {
                this.vectorStoreInstance.load(file);
                log.info("已加载本地向量库: {} ({} bytes)", vectorStorePath, file.length());
            } catch (Exception e) {
                log.warn("加载本地向量库失败，将以空库启动: {}", e.getMessage());
            }
        }
        return this.vectorStoreInstance;
    }

    /**
     * 优雅停机时持久化向量，避免进程被杀导致已向量化的数据丢失。
     */
    @PreDestroy
    public void persistOnShutdown() {
        if (this.vectorStoreInstance == null) {
            return;
        }
        try {
            this.vectorStoreInstance.save(new File(vectorStorePath));
            log.info("已持久化向量库: {}", vectorStorePath);
        } catch (Exception e) {
            log.warn("持久化向量库失败: {}", e.getMessage());
        }
    }
}