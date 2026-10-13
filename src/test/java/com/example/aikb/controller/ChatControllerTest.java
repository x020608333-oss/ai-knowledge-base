package com.example.aikb.controller;

import com.example.aikb.common.Result;
import com.example.aikb.dto.ChatRequest;
import com.example.aikb.dto.RetrievedChunk;
import com.example.aikb.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 聊天控制器切片测试：验证 SSE 流式问答、纯检索接口与参数校验。
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private RagService ragService;

    @Test
    void streamPost_returnsEventStream() throws Exception {
        when(ragService.chat(any(), eq("年假多少天")))
                .thenReturn(Flux.just("15 天", "__REFERENCES__[]"));

        ChatRequest req = new ChatRequest();
        req.setQuestion("年假多少天");
        req.setSessionId("1");

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void streamPost_blankQuestion_returns400() throws Exception {
        // question 为空触发 @NotBlank 校验
        ChatRequest req = new ChatRequest();
        req.setQuestion("");

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retrieve_returnsResult() throws Exception {
        when(ragService.retrieve(any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(post("/api/chat/retrieve")
                        .param("question", "RAG")
                        .param("topK", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
