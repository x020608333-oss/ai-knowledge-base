package com.example.aikb.controller;

import com.example.aikb.service.KbDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库文档上传控制器测试：空文件校验与正常上传返回。
 */
@WebMvcTest(KbDocumentController.class)
class KbDocumentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private KbDocumentService documentService;

    @Test
    void upload_emptyFile_returnsError() throws Exception {
        MockMultipartFile empty =
                new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/kb/upload").file(empty))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        // 空文件应被拦截，不触发后续处理
        verifyNoInteractions(documentService);
    }

    @Test
    void upload_validFile_returnsDocId() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        when(documentService.uploadDocument(any())).thenReturn(99L);

        mockMvc.perform(multipart("/api/kb/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.docId").value(99));
    }
}
