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

    /** SSE 会话前缀标记：新建会话时把 sessionId 回传前端，保证多轮记忆可用 */
    private static final String SESSION_MARKER = "__SESSION__";

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
            // ---- 1. 准备会话（校验并复用存在的 session，否则新建） ----
            Long requestedSid = parseSessionId(sessionId);
            String sid = (requestedSid == null || sessionMapper.selectById(requestedSid) == null)
                    ? createSession(question) : String.valueOf(requestedSid);

            // ---- 2. 加载历史记忆 ----
            List<Message> history = loadHistory(sid);

            // ---- 3. 检索召回（引用只包含真正进入上下文的片段） ----
            List<RetrievedChunk> chunks = retrieve(question, ragProperties.getTopK());
            List<RetrievedChunk> selected = selectContextChunks(chunks);
            String context = buildContext(selected);
            List<SourceReference> references = buildReferences(selected);

            // ---- 3.1 缓存命中：跳过 LLM，但保留多轮消息与引用 ----
            String cached = getCachedAnswer(question);
            if (cached != null && !cached.isBlank()) {
                saveMessage(sid, "user", question, null);
                saveMessage(sid, "assistant", cached, serializeReferences(references));
                return Flux.concat(
                        Flux.just(SESSION_MARKER + sid),
                        Flux.just(cached),
                        referenceTail(references));
            }

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

            return Flux.concat(
                    Flux.just(SESSION_MARKER + sid),
                    stream.concatWith(referenceTail(references)));
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
            RetrievedChunk chunk = new RetrievedChunk(
                    fileName == null ? "未知来源" : fileName.toString(),
                    doc.getText(),
                    doc.getScore() == null ? 0.0 : doc.getScore());
            chunk.setPage(extractPage(doc));
            chunks.add(chunk);
        }
        return chunks;
    }

    private Integer extractPage(Document doc) {
        Object page = doc.getMetadata().get("page_number");
        if (page == null) {
            page = doc.getMetadata().get("page");
        }
        if (page instanceof Number number) {
            // Spring AI 的 PagePdfDocumentReader 页码从 0 开始，展示时转为 1-based
            return number.intValue() + 1;
        }
        if (page != null) {
            try {
                return Integer.parseInt(page.toString()) + 1;
            } catch (NumberFormatException ignored) {
                // 非数字页码直接忽略
            }
        }
        return null;
    }

    // ============================ 内部辅助方法 ============================

    private Long parseSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(sessionId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 只保留能进入 Prompt 的片段，保证引用编号与实际上下文一致 */
    private List<RetrievedChunk> selectContextChunks(List<RetrievedChunk> chunks) {
        List<RetrievedChunk> selected = new ArrayList<>();
        int total = 0;
        for (RetrievedChunk chunk : chunks) {
            String block = "\n[" + (selected.size() + 1) + "] 来源：" + chunk.getFileName()
                    + "\n" + chunk.getContent() + "\n";
            if (total + block.length() > ragProperties.getMaxContextChars()) {
                break;
            }
            selected.add(chunk);
            total += block.length();
        }
        return selected;
    }

    private String serializeReferences(List<SourceReference> references) {
        try {
            return objectMapper.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            log.error("序列化引用来源失败", e);
            return null;
        }
    }

    private String getCachedAnswer(String question) {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache == null) {
                return null;
            }
            Cache.ValueWrapper wrapper = cache.get(cacheKey(question));
            return wrapper == null ? null : (String) wrapper.get();
        } catch (Exception e) {
            log.warn("读取答案缓存失败", e);
            return null;
        }
    }

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
            refs.add(new SourceReference(i + 1, c.getFileName(), c.getScore(), snippet, c.getPage()));
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
        return "rag:cache:" + question;
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
