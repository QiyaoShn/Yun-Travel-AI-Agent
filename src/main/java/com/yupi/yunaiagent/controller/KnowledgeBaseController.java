package com.yupi.yunaiagent.controller;

import com.yupi.yunaiagent.init.KnowledgeBaseInitializer;
import com.yupi.yunaiagent.store.DocumentStoreService;
import com.yupi.yunaiagent.store.ElasticsearchIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识库管理接口
 * 提供知识库状态查看、初始化、重建等管理功能
 */
@Slf4j
@RestController
@RequestMapping("/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseInitializer knowledgeBaseInitializer;
    private final DocumentStoreService documentStoreService;
    private final ElasticsearchIndexManager indexManager;

    /**
     * 获取知识库状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        KnowledgeBaseInitializer.KnowledgeBaseStatus status = knowledgeBaseInitializer.getStatus();
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalDocuments", status.totalDocuments());
        result.put("vectorized", status.vectorized());
        result.put("pending", status.pending());
        result.put("progress", String.format("%.2f%%", status.getProgress()));
        result.put("elasticsearchHealthy", indexManager.healthCheck());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 手动触发知识库重建
     */
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuild() {
        try {
            knowledgeBaseInitializer.rebuild();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "知识库重建完成");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("知识库重建失败: {}", e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "知识库重建失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 手动触发增量更新
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        try {
            knowledgeBaseInitializer.incrementalUpdate();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "增量同步完成");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("增量同步失败: {}", e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "增量同步失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 重新向量化未处理的文档
     */
    @PostMapping("/vectorize")
    public ResponseEntity<Map<String, Object>> reVectorize() {
        try {
            int count = documentStoreService.reVectorizeUnprocessed();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("vectorized", count);
            result.put("message", "向量化完成，共处理 " + count + " 条文档");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("向量化失败: {}", e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "向量化失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        
        boolean esHealthy = indexManager.healthCheck();
        result.put("elasticsearch", esHealthy ? "healthy" : "unhealthy");
        result.put("timestamp", System.currentTimeMillis());
        
        if (!esHealthy) {
            result.put("message", "Elasticsearch 连接异常，请检查服务状态");
            return ResponseEntity.status(503).body(result);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 删除指定文档
     */
    @DeleteMapping("/document/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        try {
            documentStoreService.deleteDocument(id);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "文档 " + id + " 已删除");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("删除文档失败: {}", e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "删除文档失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
