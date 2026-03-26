package com.yupi.yunaiagent.chatmemory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis持久化的对话记忆
 **/
@Component
@Slf4j
public class RedisBasedChatMemory implements ChatMemory {

    private final RedisTemplate<String, List> redisTemplate;

    // Redis Key的前缀，用于区分不同业务的数据
    private static final String KEY_PREFIX = "chat:memory:";

    // 会话数据的过期时间（单位：天）
    // 3天后自动删除，可根据业务需求调整
    private static final long EXPIRATION_DAYS = 3;

    /**
     * 构造函数：注入RedisTemplate
     * @param redisTemplate 使用我们在RedisConfig中配置的chatMessageRedisTemplate
     */
    public RedisBasedChatMemory(@Qualifier("chatMessageRedisTemplate") RedisTemplate<String, List> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("RedisBasedChatMemory initialized successfully");
    }

    /**
     * 添加新消息到会话中
     * @param conversationId 会话ID
     * @param messages 要添加的新消息列表
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = getConversationKey(conversationId);

        // 获取现有消息列表（如果不存在则返回空列表）
        List<Message> existingMessages = getOrCreateConversation(conversationId);

        // 追加新消息
        existingMessages.addAll(messages);

        // 保存到Redis
        redisTemplate.opsForValue().set(key, existingMessages);

        // 设置过期时间（每次添加消息都刷新过期时间）
        redisTemplate.expire(key, EXPIRATION_DAYS, TimeUnit.DAYS);

        log.debug("Added {} messages to conversation: {}, total messages: {}",
                messages.size(), conversationId, existingMessages.size());
    }

    /**
     * 获取指定会话的所有消息
     *
     * @param conversationId 会话ID
     * @return 消息列表（如果会话不存在，返回空列表）
     */
    @Override
    public List<Message> get(String conversationId) {
        List<Message> messages = getOrCreateConversation(conversationId);
        log.debug("Retrieved {} messages from conversation: {}", messages.size(), conversationId);
        return messages;
    }

    /**
     * 清除指定会话的所有消息
     *
     * @param conversationId 会话ID
     */
    @Override
    public void clear(String conversationId) {
        String key = getConversationKey(conversationId);
        Boolean deleted = redisTemplate.delete(key);
        log.info("Cleared conversation: {}, success: {}", conversationId, deleted);
    }

    /**
     * 获取或创建会话消息列表（私有辅助方法）
     *
     * @param conversationId 会话ID
     * @return 消息列表（永远不返回null）
     */
    private List<Message> getOrCreateConversation(String conversationId) {
        String key = getConversationKey(conversationId);

        // 从Redis获取数据
        List messages = redisTemplate.opsForValue().get(key);

        // 如果Redis中没有数据，返回新的空列表
        if (messages == null) {
            return new ArrayList<>();
        }

        // 类型转换（Redis返回的是List类型，需要转为List<Message>）
        return new ArrayList<>(messages);
    }

    /**
     * 生成Redis Key（私有辅助方法）
     *
     * @param conversationId 会话ID
     * @return 完整的Redis Key（例如："chat:memory:session123"）
     */
    private String getConversationKey(String conversationId) {
        return KEY_PREFIX + conversationId;
    }
}

