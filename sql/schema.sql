-- ============================================================
-- AI 智能知识库问答平台 - MySQL 业务库建表脚本
-- 数据库：ai_knowledge_base
-- ============================================================
CREATE DATABASE IF NOT EXISTS ai_knowledge_base
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_knowledge_base;

-- 知识库文档元信息表
CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    file_name    VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type    VARCHAR(16)           DEFAULT NULL COMMENT '文件类型 pdf/docx/md/txt',
    file_size    BIGINT                DEFAULT NULL COMMENT '文件大小(字节)',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED',
    chunk_count  INT                   DEFAULT 0 COMMENT '切分后的分块数量',
    error_msg    VARCHAR(500)          DEFAULT NULL COMMENT '失败原因',
    create_time  DATETIME     NOT NULL COMMENT '创建时间',
    update_time  DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库文档表';

-- 会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGINT   NOT NULL COMMENT '会话ID',
    title       VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT   NOT NULL COMMENT '主键',
    session_id  BIGINT   NOT NULL COMMENT '所属会话ID',
    role        VARCHAR(16) NOT NULL COMMENT 'user/assistant',
    content     MEDIUMTEXT COMMENT '消息内容',
    references  TEXT COMMENT '引用来源(JSON)',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_session_time (session_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天消息表';
