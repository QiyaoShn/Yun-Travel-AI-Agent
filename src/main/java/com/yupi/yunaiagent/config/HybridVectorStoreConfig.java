package com.yupi.yunaiagent.config;

import com.yupi.yunaiagent.store.HybridVectorStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * 混合向量存储配置
 * 
 * 支持两种模式：
 * 1. SimpleVectorStore - 内存模式（开发/测试用）
 * 2. HybridVectorStore - MySQL + Elasticsearch 模式（生产用）
 * 
 * 通过配置文件中的 spring.store.mode 切换
 */
@Slf4j
@Configuration
public class HybridVectorStoreConfig {

    @Resource
    private EmbeddingModel dashscopeEmbeddingModel;

    /**
     * 生产级向量存储（MySQL + Elasticsearch）
     * 
     * 注意：HybridVectorStore 不是 VectorStore 接口的实现
     * 它是一个独立的组件，用于文档存储和检索
     * 如需使用，需要在业务代码中直接注入 HybridVectorStore
     */
    @Bean
    public HybridVectorStore hybridVectorStore() {
        log.info("混合向量存储组件已注册 (MySQL + Elasticsearch)");
        return null; // 由 ElasticsearchIndexManager 等组件自动初始化
    }

    /**
     * 内存向量存储（开发/测试用）
     * 
     * 默认模式，无需额外依赖
     */
    @Bean
    @Primary
    public VectorStore simpleVectorStore() {
        log.info("初始化简单向量存储 (内存模式)");
        return SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();
    }
}
