package com.example.aikb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 智能知识库问答平台 - 启动类
 *
 * @EnableAsync 开启异步处理，用于文档解析/向量化等耗时任务的异步执行
 */
@EnableAsync
@SpringBootApplication
public class AiKnowledgeBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiKnowledgeBaseApplication.class, args);
        System.out.println("""

                ============================================================
                    🧠 AI 智能知识库问答平台启动成功！
                    API 文档: http://localhost:8080/swagger-ui.html
                ============================================================
                """);
    }
}
