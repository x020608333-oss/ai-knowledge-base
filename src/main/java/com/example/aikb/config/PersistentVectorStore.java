package com.example.aikb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 带磁盘持久化的向量库：SimpleVectorStore + 每次写入后自动落盘 JSON。
 *
 * 设计要点：
 * <ul>
 *   <li>继承 SimpleVectorStore，重写 doAdd / doDelete —— 这是父类 add()/delete() 的最终落点
 *       （AbstractObservationVectorStore.add() → doAdd()），因此无需改动任何调用方，
 *       所有向量写入都会自动触发持久化。</li>
 *   <li>构造时若文件已存在则自动加载，实现「重启后知识库不丢」。</li>
 *   <li>存储路径由 {@code app.vector-store.path} 配置，支持 ${} 占位与环境变量覆盖，
 *       默认为 {@code ${user.dir}/data/vector-store.json}（当前工作目录下），避免写死在某个盘符。</li>
 * </ul>
 */
@Slf4j
public class PersistentVectorStore extends SimpleVectorStore {

    private final Path storePath;

    public PersistentVectorStore(EmbeddingModel embeddingModel, String path) {
        super(SimpleVectorStore.builder(embeddingModel));
        this.storePath = Paths.get(path).toAbsolutePath().normalize();
    }

    /** 首次创建 Bean 后调用：加载历史向量，避免每次重启都清空知识库。 */
    public void init() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            File file = storePath.toFile();
            if (file.exists() && file.length() > 0) {
                load(file);
                log.info("向量库已从磁盘加载: {}", storePath);
            } else {
                log.info("向量库文件不存在，将创建新的空库: {}", storePath);
            }
        } catch (Exception e) {
            log.warn("加载向量库文件失败，将以空库启动: {}", storePath, e);
        }
    }

    /** 当前向量库的落盘路径（便于日志与排查）。 */
    public Path getStorePath() {
        return storePath;
    }

    @Override
    public void doAdd(List<Document> documents) {
        super.doAdd(documents);
        persist();
    }

    @Override
    public void doDelete(List<String> idList) {
        super.doDelete(idList);
        persist();
    }

    /** 序列化内存中的向量到本地文件；失败不影响主流程（内存数据仍在）。 */
    private synchronized void persist() {
        try {
            save(storePath.toFile());
            log.debug("向量库已落盘: {}", storePath);
        } catch (Exception e) {
            log.error("向量库落盘失败: {}", storePath, e);
        }
    }
}
