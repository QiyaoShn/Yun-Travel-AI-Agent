package com.yupi.yunaiagent.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.yupi.yunaiagent.config.ElasticsearchIndexProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 索引管理服务
 * 负责创建、管理文旅知识库的向量索引
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexManager {

    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchIndexProperties indexProperties;

    /**
     * 初始化索引
     */
    @PostConstruct
    public void initIndex() {
        try {
            createIndexIfNotExists();
        } catch (Exception e) {
            log.error("初始化Elasticsearch索引失败: {}", e.getMessage());
        }
    }

    /**
     * 如果索引不存在则创建
     */
    public void createIndexIfNotExists() throws IOException {
        String indexName = indexProperties.getIndexName();
        
        boolean exists = elasticsearchClient.indices()
                .exists(ExistsRequest.of(e -> e.index(indexName)))
                .value();

        if (!exists) {
            createIndex();
            log.info("成功创建 Elasticsearch 索引: {}", indexName);
        } else {
            log.info("Elasticsearch 索引已存在: {}", indexName);
        }
    }

    /**
     * 创建向量索引
     */
    private void createIndex() throws IOException {
        String indexName = indexProperties.getIndexName();
        int dimensions = indexProperties.getDimensions();

        CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                .index(indexName)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                )
                .mappings(m -> m
                        .properties("id", p -> p.keyword(k -> k))
                        .properties("content", p -> p.text(t -> t.analyzer("standard")))
                        .properties("summary", p -> p.text(t -> t.analyzer("standard")))
                        .properties("category", p -> p.keyword(k -> k))
                        .properties("source_file", p -> p.keyword(k -> k))
                        .properties("metadata", p -> p.object(o -> o.enabled(true)))
                        .properties("embedding", p -> p
                                .denseVector(dv -> dv
                                        .dims(dimensions)
                                        .index(true)
                                        .similarity(indexProperties.getSimilarity())
                                        .indexOptions(io -> io
                                                .type("hnsw")
                                                .m(16)
                                                .efConstruction(100)
                                        )
                                )
                        )
                        .properties("created_at", p -> p.date(d -> d))
                )
        );

        elasticsearchClient.indices().create(request);
    }

    /**
     * 批量索引文档
     */
    public int bulkIndex(List<Map<String, Object>> documents) throws IOException {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        String indexName = indexProperties.getIndexName();
        List<BulkOperation> operations = new ArrayList<>();

        for (Map<String, Object> doc : documents) {
            Map<String, Object> indexDoc = new HashMap<>(doc);
            if (!indexDoc.containsKey("id")) {
                indexDoc.put("id", java.util.UUID.randomUUID().toString());
            }
            indexDoc.put("created_at", java.time.Instant.now().toString());

            operations.add(BulkOperation.of(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .id(String.valueOf(indexDoc.get("id")))
                            .document(indexDoc)
                    )
            ));
        }

        BulkRequest bulkRequest = BulkRequest.of(b -> b.operations(operations));
        BulkResponse response = elasticsearchClient.bulk(bulkRequest);

        if (response.errors()) {
            log.error("批量索引存在错误");
            response.items().forEach(item -> {
                if (item.error() != null) {
                    log.error("文档 {} 索引失败: {}", item.id(), item.error().reason());
                }
            });
        }

        int successCount = (int) response.items().stream()
                .filter(item -> item.error() == null)
                .count();

        log.info("批量索引完成: 成功 {} 条, 失败 {} 条", successCount, documents.size() - successCount);
        return successCount;
    }

    /**
     * 搜索相似文档（使用 more_like_this 替代 knn 搜索）
     */
    public List<Map<String, Object>> searchSimilar(float[] queryVector, int topK, String categoryFilter) throws IOException {
        String indexName = indexProperties.getIndexName();

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                .index(indexName)
                .size(topK)
                .query(q -> q
                        .bool(b -> {
                            // 使用 more_like_this 查询
                            b.must(m -> m
                                    .moreLikeThis(mlt -> mlt
                                            .fields("content", "summary")
                                            .like(l -> l.text(implodeFloats(queryVector)))
                                    )
                            );
                            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                                b.filter(f -> f
                                        .term(t -> t
                                                .field("category")
                                                .value(categoryFilter)
                                        )
                                );
                            }
                            return b;
                        })
                );

        searchBuilder.source(src -> src.filter(f -> f
                .includes(List.of("id", "content", "summary", "category", "source_file", "metadata"))
        ));

        SearchResponse<Map> response = elasticsearchClient.search(searchBuilder.build(), Map.class);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = (Map<String, Object>) hit.source();
                doc.put("_score", hit.score());
                results.add(doc);
            }
        }

        return results;
    }

    /**
     * 简单文本搜索（关键词匹配）
     */
    public List<Map<String, Object>> textSearch(String queryText, int topK, String categoryFilter) throws IOException {
        String indexName = indexProperties.getIndexName();

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                .index(indexName)
                .size(topK)
                .query(q -> q
                        .bool(b -> {
                            b.must(m -> m
                                    .match(mt -> mt
                                            .field("content")
                                            .query(queryText)
                                    )
                            );
                            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                                b.filter(f -> f
                                        .term(t -> t
                                                .field("category")
                                                .value(categoryFilter)
                                        )
                                );
                            }
                            return b;
                        })
                );

        SearchResponse<Map> response = elasticsearchClient.search(searchBuilder.build(), Map.class);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = (Map<String, Object>) hit.source();
                doc.put("_score", hit.score());
                results.add(doc);
            }
        }

        return results;
    }

    /**
     * 删除文档
     */
    public void deleteDocument(String documentId) throws IOException {
        String indexName = indexProperties.getIndexName();
        elasticsearchClient.delete(DeleteRequest.of(d -> d
                .index(indexName)
                .id(documentId)
        ));
    }

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        try {
            return elasticsearchClient.ping().value();
        } catch (Exception e) {
            log.error("Elasticsearch 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将 float 数组转为简化字符串（用于 more_like_this）
     */
    private String implodeFloats(float[] array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(array.length, 50); i++) {
            if (i > 0) sb.append(" ");
            sb.append(Float.toString(array[i]));
        }
        return sb.toString();
    }
}
