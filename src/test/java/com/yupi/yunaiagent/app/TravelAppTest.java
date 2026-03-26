package com.yupi.yunaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class TravelAppTest {

    @Resource
    private TravelApp travelApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮：开场问候
        String message = "你好，我想去峨眉山玩几天";
        String answer = travelApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第二轮：询问景点
        message = "金顶和万年寺哪个更值得去？";
        answer = travelApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮：继续对话
        message = "如果我只有两天时间，怎么安排比较好？";
        answer = travelApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "我计划下周带家人（两大一小，孩子8岁）去峨眉山三日游，请帮我规划行程";
        TravelApp.TravelReport travelReport = travelApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(travelReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "峨眉山门票价格是多少？有没有优惠政策？";
        String answer = travelApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索：获取最新旅游资讯
        testMessage("帮我搜索一下峨眉山最近的天气情况和穿衣建议");

        // 测试网页抓取：获取旅游攻略
        testMessage("搜索一下小红书上关于峨眉山徒步的热门攻略");

        // 测试资源下载：下载景区地图
        testMessage("帮我下载一张峨眉山景区的最新高清地图到本地");

        // 测试文件操作：保存行程规划
        testMessage("把我们的三日游行程规划保存为文件");

        // 测试 PDF 生成：生成详细行程单
        testMessage("帮我生成一份峨眉山两日精华游的详细行程单PDF，包含每日路线、时间安排和注意事项");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = travelApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试地图 MCP：周边景点推荐
        // String message = "我在成都，准备去峨眉山，请帮我推荐沿途值得停留的景点";
        // String answer = travelApp.doChatWithMcp(message, chatId);
        // Assertions.assertNotNull(answer);

        // 测试图片搜索 MCP：景点图片
        String message = "Help me search for some pictures of the Golden Summit of Mount Emei in Sichuan Province, China.";
        String answer = travelApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }

}
