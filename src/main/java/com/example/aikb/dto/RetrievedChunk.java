package com.example.aikb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索到的上下文片段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunk {

    private String fileName;
    private String content;
    private Double score;
    private Integer page;

    public RetrievedChunk(String fileName, String content, Double score) {
        this(fileName, content, score, null);
    }
}
