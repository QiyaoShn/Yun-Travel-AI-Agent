package com.yupi.yunaiagent.app;

import com.yupi.yunaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yunaiagent.advisor.ReReadingAdvisor;
import com.yupi.yunaiagent.chatmemory.FileBasedChatMemory;
import com.yupi.yunaiagent.chatmemory.RedisBasedChatMemory;
import com.yupi.yunaiagent.rag.TravelRagCustomAdvisorFactory;
import com.yupi.yunaiagent.rag.QueryRewriter;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class TravelApp {

    @Resource
    private QueryRewriter  queryRewriter;

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "你是一位专业的峨眉山文旅助手，专注于为游客提供全面的旅行服务。\n" +
            "开场友好问候，告知用户你可以帮助他们规划峨眉山旅行路线、推荐景点、介绍美食和住宿。\n" +
            "围绕以下方面与用户交流：\n" +
            "1. 景点咨询：介绍金顶、万年寺、清音阁等经典景点的特色和游览建议\n" +
            "2. 路线规划：根据用户的时间和偏好，规划最佳游览路线\n" +
            "3. 实用信息：提供门票、交通、住宿、餐饮等实用信息\n" +
            "4. 注意事项：提醒游客注意天气变化、安全事项、最佳游览季节等\n" +
            "请引导用户详细描述他们的旅行需求：想去哪些景点、同行人数、计划天数等信息，以便给出专属的旅行建议。";

    /**
     * 初始化 ChatClient
     * @param dashscopeChatModel
     */
    public TravelApp(ChatModel dashscopeChatModel, RedisBasedChatMemory redisBasedChatMemory) {
        //方法3：初始化基于Redis存储的对话记忆-持久化保存并且高性能、支持分布式
        ChatMemory chatMemory = redisBasedChatMemory;
//        //方法2：初始化基于文件的对话记忆-持久化保存
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
//        // 方法1：初始化基于内存的对话记忆-重启服务器丢失
//        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
//                .chatMemoryRepository(new InMemoryChatMemoryRepository())
//                .maxMessages(20)
//                .build();

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                        //自定义日志Advisor，可按需开启
//                        new MyLoggerAdvisor()
                        //自定义推理增强Advisor，可按需开启
//                        new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE流式传输）
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    record TravelReport(String title, List<String> suggestions) {
    }

    /**
     * AI 旅行规划报告功能（实战结构化输出）
     * @param message
     * @param chatId
     * @return
     */
    public TravelReport doChatWithReport(String message, String chatId) {
        TravelReport travelReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成旅行规划报告，标题为{用户名}的峨眉山旅行规划，内容为多个建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(TravelReport.class);
        log.info("travelReport: {}", travelReport);
        return travelReport;
    }

    @Resource
    private VectorStore travelVectorStore;

    /**
     * 和 RAG 知识库进行对话
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);

        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                // 应用RAG知识库问答
                .advisors(QuestionAnswerAdvisor.builder(travelVectorStore).build())
                // 应用自定义的RAG检索增强服务（文档查询器+上下文增强器）
//                .advisors(
//                        TravelRagCustomAdvisorFactory.createTravelRagCustomAdvisor(
//                                travelVectorStore, "徒步"
//                        )
//                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
    // AI 调用工具能力
    @Resource
    private ToolCallback[] allTools;

    /**
     * AI 旅行规划功能（支持调用工具）
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    //AI 调用MCP服务
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    /**
     * AI 旅行规划功能（调用高德地图MCP服务）
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }


}

