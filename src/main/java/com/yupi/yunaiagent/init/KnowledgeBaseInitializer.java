package com.yupi.yunaiagent.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yupi.yunaiagent.data.entity.TravelDocumentEntity;
import com.yupi.yunaiagent.data.mapper.TravelDocumentMapper;
import com.yupi.yunaiagent.rag.TravelDocumentLoader;
import com.yupi.yunaiagent.store.DocumentStoreService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库初始化服务
 * 
 * 功能：
 * 1. 启动时检查并初始化知识库
 * 2. 将 Markdown 文档加载并存储到 MySQL + Elasticsearch
 * 3. 支持增量更新（只存储新增文档）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseInitializer {

    private final TravelDocumentLoader documentLoader;
    private final DocumentStoreService documentStoreService;
    private final TravelDocumentMapper documentMapper;

    @Value("${knowledge-base.auto-init:true}")
    private boolean autoInit;

    @Value("${knowledge-base.init-on-startup:false}")
    private boolean initOnStartup;

    /**
     * 启动时自动初始化知识库
     */
    @PostConstruct
    public void init() {
        if (!initOnStartup) {
            log.info("知识库自动初始化已禁用 (knowledge-base.init-on-startup=false)");
            return;
        }

        log.info("开始初始化知识库...");
        try {
            // 1. 检查知识库是否已有数据
            long existingCount = getExistingDocumentCount();
            log.info("当前知识库文档数量: {}", existingCount);

            if (existingCount == 0) {
                // 首次初始化：从 Markdown 文件加载并存储
                log.info("首次初始化：从 Markdown 文件加载文档...");
                initializeFromMarkdown();
            } else {
                // 增量更新：检查新增文档
                log.info("增量更新：检查新增文档...");
                incrementalUpdate();
            }

            // 2. 重新向量化未处理的文档
            int reVectorized = documentStoreService.reVectorizeUnprocessed();
            log.info("重新向量化完成: {} 条文档", reVectorized);

            log.info("知识库初始化完成！");

        } catch (Exception e) {
            log.error("知识库初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从 Markdown 文件初始化知识库
     */
    public void initializeFromMarkdown() {
        // 1. 加载 Markdown 文档
        List<Document> documents = documentLoader.loadMarkdowns();
        log.info("加载 Markdown 文档: {} 篇", documents.size());

        if (documents.isEmpty()) {
            log.warn("未找到 Markdown 文档，请检查 document 目录");
            return;
        }

        // 2. 存储到 MySQL + Elasticsearch
        int stored = documentStoreService.storeDocuments(documents);
        log.info("文档存储完成: {} 篇", stored);
    }

    /**
     * 增量更新：检查并添加新文档
     */
    public void incrementalUpdate() {
        // 1. 获取已存储的文档文件名
        List<String> storedFiles = getStoredSourceFiles();
        log.info("已存储的文件: {}", storedFiles);

        // 2. 加载 Markdown 文档
        List<Document> allDocuments = documentLoader.loadMarkdowns();
        
        // 3. 过滤出新增文档
        List<Document> newDocuments = allDocuments.stream()
                .filter(doc -> {
                    String filename = doc.getMetadata().getOrDefault("filename", "").toString();
                    return !storedFiles.contains(filename);
                })
                .toList();

        if (newDocuments.isEmpty()) {
            log.info("没有新增文档");
            return;
        }

        log.info("发现新增文档: {} 篇", newDocuments.size());
        
        // 4. 存储新文档
        int stored = documentStoreService.storeDocuments(newDocuments);
        log.info("新增文档存储完成: {} 篇", stored);
    }

    /**
     * 获取已存储的文档数量
     */
    private long getExistingDocumentCount() {
        LambdaQueryWrapper<TravelDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelDocumentEntity::getDeleted, 0);
        return documentMapper.selectCount(wrapper);
    }

    /**
     * 获取已存储的源文件列表
     */
    private List<String> getStoredSourceFiles() {
        LambdaQueryWrapper<TravelDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelDocumentEntity::getDeleted, 0)
               .select(TravelDocumentEntity::getSourceFile)
               .isNotNull(TravelDocumentEntity::getSourceFile);
        return documentMapper.selectList(wrapper)
                .stream()
                .map(TravelDocumentEntity::getSourceFile)
                .distinct()
                .toList();
    }

    /**
     * 手动触发全量重建
     */
    public void rebuild() {
        log.info("开始全量重建知识库...");
        initializeFromMarkdown();
        log.info("知识库全量重建完成");
    }

    /**
     * 获取知识库状态
     */
    public KnowledgeBaseStatus getStatus() {
        LambdaQueryWrapper<TravelDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelDocumentEntity::getDeleted, 0);
        
        long total = documentMapper.selectCount(wrapper);
        
        LambdaQueryWrapper<TravelDocumentEntity> vectorizedWrapper = new LambdaQueryWrapper<>();
        vectorizedWrapper.eq(TravelDocumentEntity::getDeleted, 0)
                        .eq(TravelDocumentEntity::getVectorized, true);
        long vectorized = documentMapper.selectCount(vectorizedWrapper);

        return new KnowledgeBaseStatus(total, vectorized, total - vectorized);
    }

    /**
     * 知识库状态
     */
    public record KnowledgeBaseStatus(long totalDocuments, long vectorized, long pending) {
        public double getProgress() {
            return totalDocuments == 0 ? 0 : (double) vectorized / totalDocuments * 100;
        }
    }
}
