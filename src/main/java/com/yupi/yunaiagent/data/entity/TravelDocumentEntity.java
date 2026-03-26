package com.yupi.yunaiagent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文旅知识库文档实体类
 * 存储文档的完整内容和元数据
 */
@Data
@TableName("travel_document")
public class TravelDocumentEntity {

    /**
     * 文档ID (雪花算法生成)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 文档内容（原始文本）
     */
    private String content;

    /**
     * 文档摘要（用于快速预览）
     */
    private String summary;

    /**
     * 元数据（JSON格式存储）
     * 包含: category, source_file, keywords 等
     */
    private String metadataJson;

    /**
     * 文档类别（如：景点、徒步、祈福、美食）
     */
    private String category;

    /**
     * 来源文件名
     */
    private String sourceFile;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 是否已向量化（用于批量处理状态跟踪）
     */
    private Boolean vectorized;

    /**
     * 文档状态（0-正常，1-已删除）
     */
    private Integer deleted;
}
