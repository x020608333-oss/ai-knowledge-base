package com.example.aikb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aikb.entity.KbDocument;
import com.example.aikb.mapper.KbDocumentMapper;
import com.example.aikb.service.KbDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 知识库文档服务实现。
 *
 * 处理管线：上传 → 记录元信息 → 【异步】解析原文 → 分块 → 写入向量库 → 更新状态。
 */
@Slf4j
@Service
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument>
        implements KbDocumentService {

    private final VectorStore vectorStore;
    private final Executor ragTaskExecutor;

    public KbDocumentServiceImpl(VectorStore vectorStore,
                                 @Qualifier("ragTaskExecutor") Executor ragTaskExecutor) {
        this.vectorStore = vectorStore;
        this.ragTaskExecutor = ragTaskExecutor;
    }

    @Override
    public Long uploadDocument(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        KbDocument doc = new KbDocument();
        doc.setFileName(originalName);
        doc.setFileType(resolveFileType(originalName));
        doc.setFileSize(file.getSize());
        doc.setStatus("PENDING");
        doc.setCreateTime(LocalDateTime.now());
        doc.setUpdateTime(LocalDateTime.now());
        this.save(doc);

        // 异步执行解析与向量化，立即返回，避免阻塞上传请求
        processDocumentAsync(doc.getId(), file);
        return doc.getId();
    }

    /**
     * 异步处理文档：解析 → 分块 → 向量化入库。
     */
    @Async("ragTaskExecutor")
    public void processDocumentAsync(Long docId, MultipartFile file) {
        KbDocument doc = this.getById(docId);
        if (doc == null) {
            return;
        }
        doc.setStatus("PROCESSING");
        this.updateById(doc);

        try {
            byte[] content = file.getBytes();

            // 1. 解析原文：PDF 用专用 Reader，其余（docx/md/txt）用 Tika
            List<Document> documents = parse(content, doc.getFileName(), doc.getFileType());

            // 2. 为每个片段注入来源元数据（用于后续答案溯源）
            documents.forEach(d -> {
                Map<String, Object> metadata = new HashMap<>(d.getMetadata());
                metadata.put("file_name", doc.getFileName());
                metadata.put("doc_id", docId);
                d.getMetadata().putAll(metadata);
            });

            // 3. 分块（TokenTextSplitter 会同时生成向量所需的切分）
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.apply(documents);

            // 4. 写入向量库（内部自动调用 EmbeddingModel 向量化）
            vectorStore.add(chunks);

            // 5. 更新状态
            doc.setStatus("COMPLETED");
            doc.setChunkCount(chunks.size());
            doc.setUpdateTime(LocalDateTime.now());
            this.updateById(doc);
            log.info("文档处理完成: id={}, 分块数={}", docId, chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: id={}", docId, e);
            doc.setStatus("FAILED");
            doc.setErrorMsg(e.getMessage());
            doc.setUpdateTime(LocalDateTime.now());
            this.updateById(doc);
        }
    }

    /**
     * 根据文件类型选择解析器。
     */
    private List<Document> parse(byte[] content, String fileName, String fileType) throws IOException {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        if ("pdf".equalsIgnoreCase(fileType)) {
            // PDF：去除每页头部/尾部页眉页脚，合并被硬换行截断的段落
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                            .withNumberOfBottomTextLinesToDelete(0)
                            .withNumberOfTopTextLinesToDelete(1)
                            .build())
                    .withPagesPerDocument(1)
                    .build();
            return new PagePdfDocumentReader(resource, config).get();
        }

        // docx / md / txt 等：统一走 Tika
        return new TikaDocumentReader(resource).get();
    }

    private String resolveFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "txt";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
