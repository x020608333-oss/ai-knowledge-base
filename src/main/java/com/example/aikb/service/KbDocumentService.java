package com.example.aikb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aikb.entity.KbDocument;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档服务（RAG 写入侧）。
 */
public interface KbDocumentService extends IService<KbDocument> {

    /**
     * 上传并处理文档：解析 → 清洗 → 分块 → 向量化 → 写入向量库（异步）。
     *
     * @param file 上传的文件
     * @return 文档记录 ID
     */
    Long uploadDocument(MultipartFile file);
}
