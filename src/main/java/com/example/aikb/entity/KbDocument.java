package com.example.aikb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档实体：记录一次上传文档的元信息与处理状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型：pdf/docx/md/txt */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 处理状态：PENDING / PROCESSING / COMPLETED / FAILED */
    private String status;

    /** 切分后的分块数量 */
    private Integer chunkCount;

    /** 失败原因 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
