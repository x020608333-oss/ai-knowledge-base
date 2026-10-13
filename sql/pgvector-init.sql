-- ============================================================
-- pgVector 向量库初始化脚本
-- 在 PostgreSQL 首次启动时自动执行（docker-entrypoint-initdb.d）
-- ============================================================

-- 启用 pgVector 扩展（pgvector/pgvector:pg16 镜像已内置）
CREATE EXTENSION IF NOT EXISTS vector;

-- Spring AI 的 PgVectorStore 默认会自动创建 vector_store 表（initialize-schema=true），
-- 此处仅作说明与手动兜底，字段需与 embedding 维度（当前 bge-m3 = 1024）保持一致。
-- 如需手动建表，可取消下方注释：
--
-- CREATE TABLE IF NOT EXISTS vector_store (
--     id        UUID DEFAULT gen_random_uuid() PRIMARY KEY,
--     content   TEXT,
--     metadata  JSON,
--     embedding VECTOR(1024)
-- );
--
-- CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
--     ON vector_store USING hnsw (embedding vector_cosine_ops);
