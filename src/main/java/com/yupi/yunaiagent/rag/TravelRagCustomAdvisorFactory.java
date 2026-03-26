package com.yupi.yunaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * 峨眉山文旅知识库RAG自定义检索增强顾问工厂
 * 支持按景区类型、季节等元数据进行文档过滤
 */
@Slf4j
public class TravelRagCustomAdvisorFactory {
    /**
     * 创建RAG检索增强顾问
     * @param vectorStore 向量存储
     * @param category 景区类别（如"徒步"、"亲子"、"祈福"等）
     * @return RAG检索增强顾问
     */
    public static Advisor createTravelRagCustomAdvisor(VectorStore vectorStore, String category) {
        // 过滤特定类型的景区文档
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("category", category)
                .build();
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression) // 过滤条件
                .similarityThreshold(0.5) // 相似度阈值
                .topK(3) // 返回文档数量
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(TravelRagQueryAugmenterFactory.createInstance())
                .build();
    }
}

