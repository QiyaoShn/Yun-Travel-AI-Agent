package com.yupi.yunaiagent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Elasticsearch 向量索引记录实体
 * 存储文档ID和对应的向量数据
 */
@Data
@TableName("travel_document_vector")
public class TravelDocumentVectorEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的文档ID
     */
    private Long documentId;

    /**
     * 向量数据（JSON格式存储）
     * 存储为扁平化数组以兼容MySQL
     */
    private String vectorData;

    /**
     * 向量维度（通常是1536维）
     */
    private Integer dimensions;

    /**
     * 模型名称（如：text-embedding-v3）
     */
    private String modelName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 文档状态
     */
    private Integer deleted;
}
