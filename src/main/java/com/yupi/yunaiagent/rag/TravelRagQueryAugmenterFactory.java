package com.yupi.yunaiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 峨眉山文旅知识库RAG自定义查询增强器
 */
public class TravelRagQueryAugmenterFactory {
    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                您咨询的内容超出了峨眉山文旅知识库的范围。
                抱歉，我只能回答与峨眉山旅行相关的问题，如景点介绍、路线规划、门票交通等。
                欢迎继续咨询峨眉山的旅游信息，或联系人工客服获取更多帮助。
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}

