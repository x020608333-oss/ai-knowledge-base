-- ============================================================
-- H2 兼容建表脚本（默认内存库，MODE=MySQL）
-- 由 application.yml 的 spring.sql.init 在启动时自动执行
-- 生产 MySQL 建表脚本见 sql/schema.sql
-- ============================================================
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session;
DROP TABLE IF EXISTS kb_document;

-- 知识库文档元信息表
CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    file_name    VARCHAR(255) NOT NULL,
    file_type    VARCHAR(16),
    file_size    BIGINT,
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    chunk_count  INT          DEFAULT 0,
    error_msg    VARCHAR(500),
    create_time  TIMESTAMP    NOT NULL,
    update_time  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_status ON kb_document (status);

-- 会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGINT   NOT NULL,
    title       VARCHAR(255),
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

-- 消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT NOT NULL,
    session_id  BIGINT NOT NULL,
    role        VARCHAR(16) NOT NULL,
    content     CLOB,
    references  CLOB,
    create_time TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_session_time ON chat_message (session_id, create_time);
