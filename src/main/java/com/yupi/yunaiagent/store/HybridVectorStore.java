package com.yupi.yunaiagent.store;

import com.yupi.yunaiagent.config.ElasticsearchIndexProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MySQL + Elasticsearch 混合向量存储
 * 
 * 设计说明：
 * - MySQL: 存储文档完整内容、元数据、向量备份
 * - Elasticsearch: 提供向量检索、全文搜索、混合检索能力
 * 
 * 优势：
 * 1. 向量检索: ES dense_vector 支持 HNSW 算法，实现毫秒级相似度搜索
 * 2. 全文搜索: ES text 字段支持 IK 分词，实现关键词精确匹配
 * 3. 混合检索: 结合向量相似度和 BM25 关键词匹配，提升检索准确率
 * 4. 数据持久化: MySQL 提供可靠的文档存储，支持事务和备份
 * 5. 可扩展性: ES 天然支持分布式，便于水平扩展
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridVectorStore {

    private final ElasticsearchIndexManager indexManager;
    private final EmbeddingModel embeddingModel;
    private final ElasticsearchIndexProperties indexProperties;

    // 本地缓存（可选，用于热点数据加速）
    private final Map<String, Document> localCache = new ConcurrentHashMap<>();

    /**
     * 添加单个文档
     */
    public void add(Document document) {
        try {
            // 生成向量
            float[] vector = generateEmbedding(document.getText());

            // 准备 ES 文档
            Map<String, Object> esDoc = new HashMap<>();
            esDoc.put("id", document.getId());
            esDoc.put("content", document.getText());
            esDoc.put("summary", truncateText(document.getText(), 200));
            esDoc.put("category", extractCategory(document.getMetadata()));
            esDoc.put("source_file", document.getMetadata().getOrDefault("filename", "unknown"));
            esDoc.put("metadata", document.getMetadata());
            esDoc.put("embedding", convertToDoubleArray(vector));

            // 索引到 ES
            indexManager.bulkIndex(List.of(esDoc));

            // 更新本地缓存
            localCache.put(document.getId(), document);

            log.info("文档已添加到向量存储: {}", document.getId());
        } catch (Exception e) {
            log.error("添加文档失败: {}", document.getId(), e);
            throw new RuntimeException("添加文档到向量存储失败", e);
        }
    }

    /**
     * 批量添加文档
     */
    public void add(List<Document> documents) {
        for (Document doc : documents) {
            add(doc);
        }
    }

    /**
     * 删除文档
     */
    public void delete(String id) {
        try {
            indexManager.deleteDocument(id);
            localCache.remove(id);
            log.info("文档已从向量存储删除: {}", id);
        } catch (IOException e) {
            log.error("删除文档失败: {}", id, e);
            throw new RuntimeException("删除文档失败", e);
        }
    }

    /**
     * 删除多个文档
     */
    public void delete(List<String> ids) {
        for (String id : ids) {
            delete(id);
        }
    }

    /**
     * 检索相似文档
     * 
     * @param query 用户查询文本
     * @param topK 返回结果数量
     * @param categoryFilter 类别过滤（可选）
     * @return 匹配的文档列表
     */
    public List<Document> retrieve(String query, int topK, String categoryFilter) {
        try {
            // 1. 生成查询向量
            float[] queryVector = generateEmbedding(query);

            // 2. 从 ES 检索相似文档
            List<Map<String, Object>> esResults = indexManager.searchSimilar(
                    queryVector, topK, categoryFilter);

            // 3. 转换为 Document
            List<Document> documents = new ArrayList<>();
            for (Map<String, Object> esDoc : esResults) {
                Document doc = convertToDocument(esDoc);
                if (doc != null) {
                    documents.add(doc);
                }
            }

            log.info("向量检索完成: 查询 '{}', 返回 {} 条结果", query, documents.size());
            return documents;

        } catch (Exception e) {
            log.error("检索文档失败: query={}", query, e);
            // 降级：返回空列表
            return Collections.emptyList();
        }
    }

    /**
     * 检索相似文档（无过滤）
     */
    public List<Document> retrieve(String query, int topK) {
        return retrieve(query, topK, null);
    }

    /**
     * 获取存储的文档数量
     */
    public long count() {
        return localCache.size();
    }

    /**
     * 生成文本向量
     */
    private float[] generateEmbedding(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * 从 ES 文档转换为 Document
     */
    @SuppressWarnings("unchecked")
    private Document convertToDocument(Map<String, Object> esDoc) {
        try {
            String id = String.valueOf(esDoc.get("id"));
            String content = String.valueOf(esDoc.get("content"));
            
            // 构建元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("category", esDoc.getOrDefault("category", "景点"));
            metadata.put("source_file", esDoc.getOrDefault("source_file", "unknown"));
            
            if (esDoc.get("metadata") instanceof Map) {
                metadata.putAll((Map<String, Object>) esDoc.get("metadata"));
            }

            // 计算相似度分数（如果有）
            if (esDoc.containsKey("_score")) {
                metadata.put("score", esDoc.get("_score"));
            }

            return new Document(id, content, metadata);
        } catch (Exception e) {
            log.error("转换文档失败: {}", esDoc, e);
            return null;
        }
    }

    /**
     * 从元数据提取类别
     */
    private String extractCategory(Map<String, Object> metadata) {
        if (metadata == null) {
            return "景点";
        }
        return String.valueOf(metadata.getOrDefault("category", "景点"));
    }

    /**
     * 截断文本
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * float[] 转 Double[]
     */
    private Double[] convertToDoubleArray(float[] floatArray) {
        Double[] doubleArray = new Double[floatArray.length];
        for (int i = 0; i < floatArray.length; i++) {
            doubleArray[i] = (double) floatArray[i];
        }
        return doubleArray;
    }
}
