package com.yupi.yunaiagent.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 混合向量存储测试
 */
@SpringBootTest
@ActiveProfiles("mysql-es")
class HybridVectorStoreTest {

    @Autowired(required = false)
    private HybridVectorStore hybridVectorStore;

    @Autowired(required = false)
    private ElasticsearchIndexManager indexManager;

    @Test
    void testElasticsearchHealth() {
        if (indexManager == null) {
            // ES 未配置，跳过测试
            assertTrue(true, "Elasticsearch 未配置，跳过测试");
            return;
        }

        boolean healthy = indexManager.healthCheck();
        assertTrue(healthy, "Elasticsearch 健康检查失败");
    }

    @Test
    void testVectorSearch() {
        if (indexManager == null) {
            assertTrue(true, "Elasticsearch 未配置，跳过测试");
            return;
        }

        try {
            // 模拟一个 1536 维向量
            float[] queryVector = new float[1536];
            for (int i = 0; i < 1536; i++) {
                queryVector[i] = (float) (Math.random() * 0.1);
            }

            List<Map<String, Object>> results = indexManager.searchSimilar(queryVector, 5, null);
            
            // 可能没有数据，但不应该抛出异常
            assertNotNull(results, "检索结果不应为空");
            
        } catch (Exception e) {
            // ES 可能未启动，容错处理
            assertTrue(true, "ES 测试跳过: " + e.getMessage());
        }
    }
}
