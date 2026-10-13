package com.example.aikb.controller;

import com.example.aikb.common.Result;
import com.example.aikb.service.KbDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识库文档管理控制器：负责文档上传（异步解析向量化）。
 */
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbDocumentController {

    private final KbDocumentService documentService;

    /**
     * 上传文档（PDF / DOCX / MD / TXT）。接口立即返回，解析在后台异步进行。
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        Long docId = documentService.uploadDocument(file);
        Map<String, Object> data = new HashMap<>();
        data.put("docId", docId);
        data.put("message", "文档已接收，正在后台解析与向量化，请稍后查询处理状态");
        return Result.success(data);
    }
}
