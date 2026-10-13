package com.example.aikb.service.impl;

import com.example.aikb.config.AiConfig;
import com.example.aikb.config.RagProperties;
import com.example.aikb.dto.RetrievedChunk;
import com.example.aikb.dto.SourceReference;
import com.example.aikb.entity.ChatMessage;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.mapper.ChatMessageMapper;
import com.example.aikb.mapper.ChatSessionMapper;
import com.example.aikb.service.RagService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * RAG 编排核心实现。
 *
 * 一次问答的完整流程：
 * 1. 准备会话（无 sessionId 则新建）
 * 2. 加载多轮记忆
 * 3. 向量检索召回 Top-K 相关片段
 * 4. 组装 Prompt（RAG 系统提示 + 上下文 + 历史 + 当前问题）
 * 5. 调用 LLM 流式生成
 * 6. 持久化消息，并以 SSE 尾部标记返回引用来源
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {

    /** SSE 引用来源前缀标记，前端据此拆分正文与引用 */
    private static final String REFERENCES_MARKER = "__REFERENCES__";

    /** 热点问答缓存名称（由 CacheConfig 提供 Caffeine 实现，TTL 1h） */
    private static final String CACHE_NAME = "ragAnswerCache";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final CacheManager cacheManager;
    private final Executor ragTaskExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagServiceImpl(ChatClient chatClient,
                          VectorStore vectorStore,
                          RagProperties ragProperties,
                          ChatSessionMapper sessionMapper,
                          ChatMessageMapper messageMapper,
                          CacheManager cacheManager,
                          @Qualifier("ragTaskExecutor") Executor ragTaskExecutor) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.cacheManager = cacheManager;
        this.ragTaskExecutor = ragTaskExecutor;
    }

    @Override
    public Flux<String> chat(String sessionId, String question) {
        return Flux.defer(() -> {
            // ---- 1. 准备会话 ----
            String sid = (sessionId == null || sessionId.isBlank())
                    ? createSession(question) : sessionId;

            // ---- 2. 加载历史记忆 ----
            List<Message> history = loadHistory(sid);

            // ---- 3. 检索召回 ----
            List<RetrievedChunk> chunks = retrieve(question, ragProperties.getTopK());
            String context = buildContext(chunks);
            List<SourceReference> references = buildReferences(chunks);

            // ---- 4. 组装消息 ----
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append(AiConfig.RAG_SYSTEM_PROMPT.replace("{context}", context));
            userPrompt.append("\n\n【对话历史】\n").append(renderHistory(history));
            userPrompt.append("\n【用户问题】\n").append(question);

            List<Message> messages = new ArrayList<>(history);
            messages.add(new UserMessage(userPrompt.toString()));

            // ---- 5. 持久化用户消息 ----
            saveMessage(sid, "user", question, null);

            // ---- 6. 流式生成 ----
            StringBuilder answerBuffer = new StringBuilder();
            Flux<String> stream = chatClient.prompt()
                    .messages(messages)
                    .stream()
                    .content()
                    .doOnNext(answerBuffer::append)
                    .doOnComplete(() -> {
                        try {
                            saveMessage(sid, "assistant", answerBuffer.toString(),
                                    objectMapper.writeValueAsString(references));
                            cacheAnswer(question, answerBuffer.toString());
                        } catch (JsonProcessingException e) {
                            log.error("序列化引用来源失败", e);
                        }
                    })
                    .doOnError(e -> log.error("流式生成失败, sessionId={}", sid, e));

            return stream.concatWith(referenceTail(references));
        }).subscribeOn(Schedulers.fromExecutor(ragTaskExecutor));
    }

    @Override
    public List<RetrievedChunk> retrieve(String question, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(ragProperties.getSimilarityThreshold())
                .build();
        List<Document> docs = vectorStore.similaritySearch(request);

        List<RetrievedChunk> chunks = new ArrayList<>();
        for (Document doc : docs) {
            Object fileName = doc.getMetadata().get("file_name");
            chunks.add(new RetrievedChunk(
                    fileName == null ? "未知来源" : fileName.toString(),
                    doc.getText(),
                    doc.getScore() == null ? 0.0 : doc.getScore()));
        }
        return chunks;
    }

    // ============================ 内部辅助方法 ============================

    /** 拼接检索上下文，控制最大长度，注入编号 [n] 便于溯源 */
    private String buildContext(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "（未检索到相关参考资料）";
        }
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            String block = "\n[" + (i + 1) + "] 来源：" + c.getFileName() + "\n" + c.getContent() + "\n";
            if (total + block.length() > ragProperties.getMaxContextChars()) {
                break;
            }
            sb.append(block);
            total += block.length();
        }
        return sb.toString();
    }

    private List<SourceReference> buildReferences(List<RetrievedChunk> chunks) {
        List<SourceReference> refs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            String snippet = c.getContent().length() > 120
                    ? c.getContent().substring(0, 120) + "..."
                    : c.getContent();
            refs.add(new SourceReference(i + 1, c.getFileName(), c.getScore(), snippet));
        }
        return refs;
    }

    private String renderHistory(List<Message> history) {
        if (history.isEmpty()) {
            return "（无历史对话）";
        }
        StringBuilder sb = new StringBuilder();
        for (Message m : history) {
            String role = m instanceof UserMessage ? "用户" : "助手";
            sb.append(role).append("：").append(m.getText()).append("\n");
        }
        return sb.toString();
    }

    private List<Message> loadHistory(String sid) {
        List<ChatMessage> recent = messageMapper.findRecentMessages(Long.valueOf(sid), ragProperties.getChatMemoryWindow());
        List<Message> messages = new ArrayList<>();
        // findRecentMessages 为倒序，需反转为正序（早 -> 晚）
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage m = recent.get(i);
            if ("user".equals(m.getRole())) {
                messages.add(new UserMessage(m.getContent()));
            } else {
                messages.add(new AssistantMessage(m.getContent()));
            }
        }
        return messages;
    }

    private String createSession(String question) {
        ChatSession session = new ChatSession();
        session.setId(randomId());
        session.setTitle(question.length() > 20 ? question.substring(0, 20) + "..." : question);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return String.valueOf(session.getId());
    }

    private void saveMessage(String sid, String role, String content, String references) {
        ChatMessage msg = new ChatMessage();
        msg.setId(randomId());
        msg.setSessionId(Long.valueOf(sid));
        msg.setRole(role);
        msg.setContent(content);
        msg.setReferences(references);
        msg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    /** 缓存热点问答（本地 Caffeine 缓存，TTL 1h，可平滑替换为 Redis 分布式/语义缓存） */
    private void cacheAnswer(String question, String answer) {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.put(cacheKey(question), answer);
            }
        } catch (Exception e) {
            log.warn("缓存答案失败", e);
        }
    }

    private String cacheKey(String question) {
        return "rag:cache:" + question.hashCode();
    }

    /** 在流末尾追加引用来源标记，供前端解析展示"答案溯源" */
    private Flux<String> referenceTail(List<SourceReference> references) {
        if (references.isEmpty()) {
            return Flux.empty();
        }
        try {
            String payload = REFERENCES_MARKER + objectMapper.writeValueAsString(references);
            return Flux.just(payload);
        } catch (JsonProcessingException e) {
            return Flux.empty();
        }
    }

    /** 生成正的长整型 ID（雪花 ID 的简化替代） */
    private long randomId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
