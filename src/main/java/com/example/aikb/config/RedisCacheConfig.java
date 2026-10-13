package com.example.aikb.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 分布式缓存配置（docker 生产档）：使用 Redis 作为热点问答缓存后端。
 *
 * <p>仅在 {@code spring.profiles.active=docker} 时生效，替代默认档的本地 Caffeine 缓存；</p>
 * <p>业务层（RagServiceImpl）依旧通过 Spring 缓存抽象 {@link CacheManager} 访问，与具体实现无感知，</p>
 * <p>体现「同一业务代码、可插拔缓存后端」的设计。</p>
 */
@EnableCaching
@Configuration
@Profile("docker")
public class RedisCacheConfig {

    /** 热点问答缓存名称（需与 RagServiceImpl / CacheConfig 中一致） */
    public static final String RAG_ANSWER_CACHE = "ragAnswerCache";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put(RAG_ANSWER_CACHE, RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues());
        return RedisCacheManager.builder(connectionFactory)
                .withInitialCacheConfigurations(configs)
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1))
                        .disableCachingNullValues())
                .build();
    }
}
