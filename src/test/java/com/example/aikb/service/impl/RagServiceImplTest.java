package com.example.aikb.service.impl;

import com.example.aikb.config.RagProperties;
import com.example.aikb.dto.RetrievedChunk;
import com.example.aikb.entity.ChatMessage;
import com.example.aikb.mapper.ChatMessageMapper;
import com.example.aikb.mapper.ChatSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 编排核心服务的单元测试。
 *
 * 重点验证两条主线：
 * 1. retrieve()：向量检索结果的字段映射（来源 / 正文 / 得分）与空值兜底。
 * 2. chat()：SSE 流式输出 = 会话标记 __SESSION__ + 模型正文 + 尾部 __REFERENCES__ 引用标记。
 */
@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock private ChatClient chatClient;
    @Mock private VectorStore vectorStore;
    @Mock private ChatSessionMapper sessionMapper;
    @Mock private ChatMessageMapper messageMapper;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    private RagProperties ragProperties;
    private RagServiceImpl service;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.setTopK(3);
        ragProperties.setSimilarityThreshold(0.3);
        ragProperties.setChatMemoryWindow(5);
        ragProperties.setMaxContextChars(4000);

        // 同步执行器：保证 Flux 编排在调用线程完成，便于断言
        Executor syncExecutor = Runnable::run;
        service = new RagServiceImpl(chatClient, vectorStore, ragProperties,
                sessionMapper, messageMapper, cacheManager, syncExecutor);

        // 以下 stub 并非每个用例都会命中，用 lenient 避免 strict-stubs 报错
        lenient().when(cacheManager.getCache(anyString())).thenReturn(cache);
        lenient().when(messageMapper.findRecentMessages(anyLong(), anyInt())).thenReturn(List.of());
    }

    @Test
    void retrieve_mapsDocumentsToChunks() {
        Document d1 = Document.builder()
                .text("Spring AI 支持 RAG 检索增强生成。")
                .metadata("file_name", "ai-guide.pdf")
                .score(0.91)
                .build();
        Document d2 = Document.builder()
                .text("向量数据库用于语义相似度检索。")
                .metadata("file_name", "db-note.md")
                .score(0.75)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(d1, d2));

        List<RetrievedChunk> chunks = service.retrieve("什么是 RAG", 3);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getFileName()).isEqualTo("ai-guide.pdf");
        assertThat(chunks.get(0).getScore()).isEqualTo(0.91);
        assertThat(chunks.get(0).getContent()).contains("检索增强生成");
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void retrieve_defaultsWhenMetadataOrScoreMissing() {
        // 无 file_name、无 score 的文档，应兜底为"未知来源"与 0.0
        Document doc = Document.builder().text("无来源信息").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<RetrievedChunk> chunks = service.retrieve("问题", 5);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getFileName()).isEqualTo("未知来源");
        assertThat(chunks.get(0).getScore()).isEqualTo(0.0);
    }

    @Test
    void chat_streamsAnswerWithReferenceTail() {
        Document doc = Document.builder()
                .text("公司年假制度为每年 15 天。")
                .metadata("file_name", "hr-policy.pdf")
                .score(0.88)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        // mock Spring AI 流式链：prompt().messages(list).stream().content()
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("根据制度，", "年假为 15 天。"));

        List<String> out = service.chat("123", "年假多少天").collectList().block();

        assertThat(out).isNotNull();
        // 1 段会话标记 + 2 段模型正文 + 1 段引用来源
        assertThat(out).hasSize(4);
        assertThat(out.get(0)).startsWith("__SESSION__");
        assertThat(out.get(1)).isEqualTo("根据制度，");
        assertThat(out.get(2)).isEqualTo("年假为 15 天。");
        assertThat(out.get(3)).startsWith("__REFERENCES__");
        assertThat(out.get(3)).contains("hr-policy.pdf");
        verify(messageMapper, atLeastOnce()).insert(any(ChatMessage.class));
    }

    @Test
    void chat_withoutContextHasNoReferenceTail() {
        // 检索为空时，不应追加引用来源标记
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("未找到相关资料。"));

        List<String> out = service.chat("456", "随便问问").collectList().block();

        // 1 段会话标记 + 1 段模型正文
        assertThat(out).hasSize(2);
        assertThat(out.get(0)).startsWith("__SESSION__");
        assertThat(out.get(1)).isEqualTo("未找到相关资料。");
    }
}
