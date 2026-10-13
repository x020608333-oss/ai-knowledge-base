package com.example.aikb.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 本地缓存配置：热点问答缓存。
 *
 * <p>默认使用 Caffeine（进程内高性能本地缓存），零外部依赖、随应用启动即用：</p>
 * <ul>
 *   <li>TTL 1 小时、容量上限 1000 条，兼顾命中率与内存占用；</li>
 *   <li>业务层（RagServiceImpl）通过 Spring 缓存抽象 {@link CacheManager} 访问，与具体实现解耦；</li>
 *   <li>如需切换 Redis 分布式缓存，只需启用 application-docker.yml 并替换此处 Bean，业务代码零改动。</li>
 * </ul>
 */
@Configuration
public class CacheConfig {

    /** 热点问答缓存名称 */
    public static final String RAG_ANSWER_CACHE = "ragAnswerCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(RAG_ANSWER_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(1000));
        return cacheManager;
    }
}
