package com.yupi.yunaiagent.config;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.ByteArrayOutputStream;

/**
 * Kryo Redis 序列化器
 * 使用 ThreadLocal 解决 Kryo 的线程安全问题
 */
public class KryoRedisSerializer<T> implements RedisSerializer<T> {

    // 使用 ThreadLocal 确保每个线程都有独立的 Kryo 实例
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(false);
        kryo.setInstantiatorStrategy(
                new DefaultInstantiatorStrategy(new StdInstantiatorStrategy())
        );

        return kryo;
    });

    /**
     * 序列化：对象 -> 字节数组
     */
    @Override
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }

        Kryo kryo = kryoThreadLocal.get();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {

            kryo.writeClassAndObject(output, t);
            output.flush();
            byte[] bytes = baos.toByteArray();
            return bytes;

        } catch (Exception e) {
            throw new SerializationException("Kryo serialization failed", e);
        }
    }

    /**
     * 反序列化：字节数组 -> 对象
     */
    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        Kryo kryo = kryoThreadLocal.get();

        try (Input input = new Input(bytes)) {

            // 注意：这里需要知道具体类型
            // 对于泛型 T，Kryo 需要额外的类型信息
            @SuppressWarnings("unchecked")
            T result = (T) kryo.readClassAndObject(input);

            return result;

        } catch (Exception e) {
            throw new SerializationException("Kryo deserialization failed", e);
        }

    }
    /**
     * 清理 ThreadLocal 资源（可选，防止内存泄漏）
     */
    public static void clearThreadLocal() {
        kryoThreadLocal.remove();
    }
}

