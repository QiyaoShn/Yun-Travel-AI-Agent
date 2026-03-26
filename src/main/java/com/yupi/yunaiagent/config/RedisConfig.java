package com.yupi.yunaiagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Redis配置类 - 使用 Kryo 序列化
 */
@Configuration
public class RedisConfig {

    @Bean(name = "chatMessageRedisTemplate")
    public RedisTemplate<String, List> chatMessageRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, List> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 使用 String 序列化（便于查看）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 使用 Kryo 序列化（高性能）
        KryoRedisSerializer<List> kryoSerializer = new KryoRedisSerializer<>();
        template.setValueSerializer(kryoSerializer);
        template.setHashValueSerializer(kryoSerializer);

        template.afterPropertiesSet();

        return template;
    }
}

