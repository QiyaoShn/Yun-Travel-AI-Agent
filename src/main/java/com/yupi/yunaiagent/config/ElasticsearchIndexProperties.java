package com.yupi.yunaiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 索引配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchIndexProperties {

    /**
     * 索引名称
     */
    private String indexName = "travel_documents";

    /**
     * 向量维度（阿里云 DashScope text-embedding-v3 为 1536）
     */
    private Integer dimensions = 1536;

    /**
     * 向量相似度算法：cosine（余弦相似度）或 l2_norm（欧氏距离）
     */
    private String similarity = "cosine";

    /**
     * 批量写入大小
     */
    private Integer bulkSize = 100;
}
