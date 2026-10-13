package com.example.aikb.config;

/**
 * 已废弃：原 pgVector 向量库独立配置（多数据源 + Hikari 方案）。
 *
 * 现默认使用 {@link VectorStoreConfig} 中的 SimpleVectorStore 本地向量库，零外部依赖、开箱即用。
 * 如需切回 PostgreSQL + pgVector 生产级向量库，请启用 application-docker.yml，
 * 并将本类恢复为「pgvector 专用 Hikari 连接池 + JdbcTemplate + PgVectorStore」的手动构建配置。
 */
