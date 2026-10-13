package com.example.aikb.controller;

import com.example.aikb.common.Result;
import com.example.aikb.dto.ChatRequest;
import com.example.aikb.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * RAG 智能问答控制器。
 *
 * 聊天接口使用 SSE（text/event-stream）实现打字机式流式输出。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;

    /**
     * 流式问答。
     *
     * 前端通过 EventSource / fetch 读取流；以 __REFERENCES__ 开头的最后一条
     * 为引用来源数据，其余为回答正文。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request) {
        return ragService.chat(request.getSessionId(), request.getQuestion())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * 健康检查 / 简单 GET 问答（便于浏览器直接体验）。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamGet(
            @RequestParam String question,
            @RequestParam(required = false) String sessionId) {
        return ragService.chat(sessionId, question)
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
    }

    /**
     * 纯检索调试接口：返回召回片段，不调用 LLM。
     */
    @PostMapping("/retrieve")
    public Result<?> retrieve(@RequestParam String question,
                              @RequestParam(defaultValue = "5") int topK) {
        return Result.success(ragService.retrieve(question, topK));
    }
}
