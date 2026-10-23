package com.example.aikb.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Mapper 扫描独立成配置类：放在启动类上会让 @WebMvcTest 切片测试也去
 * 创建 Mapper Bean（此时没有 SqlSessionFactory），导致控制器测试起不来。
 */
@Configuration
@MapperScan("com.example.aikb.mapper")
public class MyBatisConfig {
}
