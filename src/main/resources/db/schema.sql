-- =============================================
-- 峨眉山文旅知识库数据库初始化脚本
-- =============================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS yun_ai_agent
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE yun_ai_agent;

-- =============================================
-- 表1: travel_document - 文档内容表
-- 存储文旅知识库的完整文档内容和元数据
-- =============================================
CREATE TABLE IF NOT EXISTS travel_document (
    id BIGINT NOT NULL COMMENT '文档ID（雪花算法）',
    content TEXT NOT NULL COMMENT '文档原始内容',
    summary VARCHAR(500) COMMENT '文档摘要',
    metadata JSON COMMENT '元数据（类别、来源、关键词等）',
    category VARCHAR(50) COMMENT '文档类别：景点/徒步/祈福/美食/亲子',
    source_file VARCHAR(255) COMMENT '来源文件名',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    vectorized TINYINT(1) DEFAULT 0 COMMENT '是否已向量化：0-否，1-是',
    deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除：0-正常，1-已删除',
    PRIMARY KEY (id),
    INDEX idx_category (category),
    INDEX idx_source_file (source_file),
    INDEX idx_vectorized (vectorized),
    INDEX idx_deleted (deleted),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文旅知识库文档表';

-- =============================================
-- 表2: travel_document_vector - 文档向量表
-- 存储文档的向量数据（冗余存储，便于数据迁移）
-- =============================================
CREATE TABLE IF NOT EXISTS travel_document_vector (
    id BIGINT NOT NULL COMMENT '主键ID',
    document_id BIGINT NOT NULL COMMENT '关联文档ID',
    vector_data JSON NOT NULL COMMENT '向量数据（1536维浮点数组）',
    dimensions INT DEFAULT 1536 COMMENT '向量维度',
    model_name VARCHAR(100) DEFAULT 'text-embedding-v3' COMMENT 'Embedding模型名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_document_id (document_id),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档向量表';

-- =============================================
-- 初始化测试数据
-- =============================================
INSERT INTO travel_document (id, content, summary, metadata, category, source_file, vectorized) VALUES
(1,
 '金顶是峨眉山的最高处，海拔3079米，是观赏日出、云海、佛光的最佳地点。主要景点包括金顶金殿（华藏寺金顶金殿）和四面十方普贤金像（高48米的金佛）。建议早起登山观看日出，山顶温度较低需携带保暖衣物。',
 '峨眉山金顶景区介绍，包含日出、云海、佛光等景观',
 '{"keywords": ["金顶", "日出", "云海", "佛光", "普贤", "金殿"], "tags": ["景点", "金顶"]}',
 '景点',
 '峨眉山景区介绍.md',
 0),

(2,
 '万年寺是峨眉山历史最悠久的寺庙之一，始建于东晋。主要景点有无梁砖殿（明代建筑奇迹）、白水池和白象吸水。游客可乘坐索道到达，适合文化历史爱好者游览。',
 '峨眉山万年寺历史与景点介绍',
 '{"keywords": ["万年寺", "无梁砖殿", "东晋", "寺庙", "白水池"], "tags": ["寺庙", "历史"]}',
 '景点',
 '峨眉山景区介绍.md',
 0),

(3,
 '清音阁是峨眉山最秀美的景点之一，以山水取胜。双桥清音、一线天和自然生态猴区是主要看点。建议上午游览避开高峰，注意保管好随身物品防止猴子抢食。',
 '清音阁景区山水景观与游览建议',
 '{"keywords": ["清音阁", "双桥清音", "一线天", "猴子", "生态"], "tags": ["景点", "自然"]}',
 '景点',
 '峨眉山景区介绍.md',
 0),

(4,
 '峨眉山徒步经典两日路线：第一天从报国寺出发，经伏虎寺、清音阁、一线天到洪椿坪，约15公里需6-8小时。第二天从洪椿坪出发，经仙峰寺、洗象池、雷洞坪到金顶，约20公里需8-10小时。',
 '峨眉山经典两日徒步路线规划',
 '{"keywords": ["徒步", "路线", "报国寺", "金顶", "洪椿坪"], "tags": ["徒步", "路线规划"]}',
 '徒步',
 '峨眉山旅游攻略+-+徒步篇.md',
 0),

(5,
 '徒步装备建议：必备物品包括登山杖、舒适登山鞋、足够饮用水、高热量零食、雨具和保暖衣物。注意提前了解天气、不要单独行动、保护环境、不喂食野生猴子。',
 '徒步必备装备与注意事项',
 '{"keywords": ["装备", "登山杖", "登山鞋", "注意事项"], "tags": ["徒步", "安全"]}',
 '徒步',
 '峨眉山旅游攻略+-+徒步篇.md',
 0),

(6,
 '峨眉山是中国佛教四大名山之一，是普贤菩萨的道场。金顶祈福适合学业、事业、平安；万年寺祈福适合求子、延寿；报国寺祈福适合平安、健康。进香需衣冠整洁、不踩门槛、心诚则灵。',
 '峨眉山佛教文化与祈福攻略',
 '{"keywords": ["佛教", "祈福", "普贤", "金顶", "寺庙"], "tags": ["宗教", "文化"]}',
 '祈福',
 '峨眉山旅游攻略+-+祈福篇.md',
 0),

(7,
 '峨眉山素斋以寺庙素斋最为著名，推荐菜品包括峨眉豆腐脑、素烧鹅、罗汉斋和竹笋宴。金顶素斋餐厅和报国寺素斋馆是主要选择。地方特色菜有峨眉山珍、苦笋、雪魔芋等。',
 '峨眉山特色美食与餐饮推荐',
 '{"keywords": ["美食", "素斋", "豆腐脑", "竹笋", "餐厅"], "tags": ["美食", "餐饮"]}',
 '美食',
 '峨眉山美食指南+-+美食篇.md',
 0);
