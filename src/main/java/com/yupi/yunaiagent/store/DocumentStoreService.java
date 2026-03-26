package com.yupi.yunaiagent.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yunaiagent.data.entity.TravelDocumentEntity;
import com.yupi.yunaiagent.data.entity.TravelDocumentVectorEntity;
import com.yupi.yunaiagent.data.mapper.TravelDocumentMapper;
import com.yupi.yunaiagent.data.mapper.TravelDocumentVectorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 文档存储服务
 * 负责：1) 将文档内容存储到 MySQL
 *      2) 生成向量并存储到 Elasticsearch
 *      3) 提供文档检索能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStoreService {

    private final TravelDocumentMapper documentMapper;
    private final TravelDocumentVectorMapper vectorMapper;
    private final EmbeddingModel embeddingModel;
    private final ElasticsearchIndexManager indexManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${elasticsearch.model-name:text-embedding-v3}")
    private String embeddingModelName;

    /**
     * 批量存储文档到 MySQL 和 Elasticsearch
     */
    @Transactional
    public int storeDocuments(List<Document> documents) {
        int successCount = 0;

        for (Document doc : documents) {
            try {
                // 1. 生成向量
                float[] vector = embeddingModel.embed(doc.getText());

                // 2. 提取元数据
                Map<String, Object> metadata = new HashMap<>();
                doc.getMetadata().forEach((key, value) -> {
                    if (value != null) {
                        metadata.put(key, value);
                    }
                });

                // 3. 生成文档摘要
                String summary = doc.getText().length() > 200 
                        ? doc.getText().substring(0, 200) + "..." 
                        : doc.getText();

                // 4. 元数据转 JSON
                String metadataJson = objectMapper.writeValueAsString(metadata);

                // 5. 存储到 MySQL
                TravelDocumentEntity entity = new TravelDocumentEntity();
                entity.setContent(doc.getText());
                entity.setSummary(summary);
                entity.setMetadataJson(metadataJson);
                entity.setCategory(String.valueOf(metadata.getOrDefault("category", "景点")));
                entity.setSourceFile(String.valueOf(metadata.getOrDefault("filename", "unknown")));
                entity.setVectorized(false);
                entity.setDeleted(0);
                documentMapper.insert(entity);

                // 6. 存储向量到 MySQL
                TravelDocumentVectorEntity vectorEntity = new TravelDocumentVectorEntity();
                vectorEntity.setDocumentId(entity.getId());
                vectorEntity.setVectorData(Arrays.toString(vector));
                vectorEntity.setDimensions(vector.length);
                vectorEntity.setModelName(embeddingModelName);
                vectorEntity.setDeleted(0);
                vectorMapper.insert(vectorEntity);

                // 7. 索引到 Elasticsearch
                Map<String, Object> esDoc = new HashMap<>();
                esDoc.put("id", entity.getId().toString());
                esDoc.put("content", doc.getText());
                esDoc.put("summary", summary);
                esDoc.put("category", entity.getCategory());
                esDoc.put("source_file", entity.getSourceFile());
                esDoc.put("metadata", metadata);
                esDoc.put("embedding", convertToDoubleList(vector));

                int indexed = indexManager.bulkIndex(List.of(esDoc));

                if (indexed > 0) {
                    entity.setVectorized(true);
                    documentMapper.updateById(entity);
                    successCount++;
                    log.info("文档 {} 存储成功, ID: {}", entity.getSourceFile(), entity.getId());
                }

            } catch (Exception e) {
                log.error("存储文档失败: {}", e.getMessage(), e);
            }
        }

        log.info("批量存储完成: 成功 {} 条, 总计 {} 条", successCount, documents.size());
        return successCount;
    }

    /**
     * 从 MySQL 加载所有文档
     */
    public List<TravelDocumentEntity> loadAllDocuments() {
        LambdaQueryWrapper<TravelDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelDocumentEntity::getDeleted, 0);
        return documentMapper.selectList(wrapper);
    }

    /**
     * 根据类别查询文档
     */
    public List<TravelDocumentEntity> loadDocumentsByCategory(String category) {
        LambdaQueryWrapper<TravelDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelDocumentEntity::getDeleted, 0)
               .eq(TravelDocumentEntity::getCategory, category);
        return documentMapper.selectList(wrapper);
    }

    /**
     * 根据 ID 查询文档
     */
    public Optional<TravelDocumentEntity> getDocumentById(Long id) {
        TravelDocumentEntity entity = documentMapper.selectById(id);
        return Optional.ofNullable(entity);
    }

    /**
     * 重新向量化未处理的文档
     */
    @Transactional
    public int reVectorizeUnprocessed() {
        LambdaQueryWrapper<TravelDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelDocumentEntity::getDeleted, 0)
               .eq(TravelDocumentEntity::getVectorized, false);
        
        List<TravelDocumentEntity> unprocessedDocs = documentMapper.selectList(wrapper);
        log.info("发现 {} 条未向量化的文档", unprocessedDocs.size());

        int successCount = 0;
        for (TravelDocumentEntity entity : unprocessedDocs) {
            try {
                float[] vector = embeddingModel.embed(entity.getContent());

                TravelDocumentVectorEntity vectorEntity = new TravelDocumentVectorEntity();
                vectorEntity.setDocumentId(entity.getId());
                vectorEntity.setVectorData(Arrays.toString(vector));
                vectorEntity.setDimensions(vector.length);
                vectorEntity.setModelName(embeddingModelName);
                vectorEntity.setDeleted(0);
                vectorMapper.insert(vectorEntity);

                Map<String, Object> metadata = parseMetadataJson(entity.getMetadataJson());

                Map<String, Object> esDoc = new HashMap<>();
                esDoc.put("id", entity.getId().toString());
                esDoc.put("content", entity.getContent());
                esDoc.put("summary", entity.getSummary());
                esDoc.put("category", entity.getCategory());
                esDoc.put("source_file", entity.getSourceFile());
                esDoc.put("metadata", metadata);
                esDoc.put("embedding", convertToDoubleList(vector));

                indexManager.bulkIndex(List.of(esDoc));

                entity.setVectorized(true);
                documentMapper.updateById(entity);
                successCount++;

            } catch (Exception e) {
                log.error("重新向量化文档 {} 失败: {}", entity.getId(), e.getMessage());
            }
        }

        return successCount;
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long documentId) throws Exception {
        TravelDocumentEntity entity = documentMapper.selectById(documentId);
        if (entity != null) {
            entity.setDeleted(1);
            documentMapper.updateById(entity);
            indexManager.deleteDocument(documentId.toString());
            log.info("删除文档: {}", documentId);
        }
    }

    /**
     * 解析元数据 JSON
     */
    private Map<String, Object> parseMetadataJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (Exception e) {
            log.error("解析元数据 JSON 失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * float[] 转 List<Double>
     */
    private List<Double> convertToDoubleList(float[] floatArray) {
        List<Double> list = new ArrayList<>(floatArray.length);
        for (float v : floatArray) {
            list.add((double) v);
        }
        return list;
    }
}
