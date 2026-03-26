package com.yupi.yunaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 峨眉山文旅知识库文档加载器
 */
@Component
@Slf4j
public class TravelDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    TravelDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载多篇Markdown文档
     * @return
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            // 加载多个 Markdown 文件
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                // 从文件名中提取景区类别作为元数据
                String category = extractCategory(fileName);
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", fileName)
                        .withAdditionalMetadata("category", category)
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(reader.get());
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

    /**
     * 从文件名中提取景区类别
     * @param fileName 文件名
     * @return 景区类别
     */
    private String extractCategory(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        if (fileName.contains("徒步")) {
            return "徒步";
        } else if (fileName.contains("祈福")) {
            return "祈福";
        } else if (fileName.contains("美食")) {
            return "美食";
        } else if (fileName.contains("亲子")) {
            return "亲子";
        }
        return "景点";
    }
}

