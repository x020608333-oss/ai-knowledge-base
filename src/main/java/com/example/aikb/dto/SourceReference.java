package com.example.aikb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用来源：回答所依据的知识库片段，用于"答案溯源"。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceReference {

    /** 片段序号（对应 Prompt 中的 [n] 编号） */
    private Integer index;

    /** 来源文件名 */
    private String fileName;

    /** 相似度得分 */
    private Double score;

    /** 片段内容摘要 */
    private String snippet;

    /** PDF 页码（从 1 开始；非 PDF 为 null） */
    private Integer page;

    public SourceReference(Integer index, String fileName, Double score, String snippet) {
        this(index, fileName, score, snippet, null);
    }
}
