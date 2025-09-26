package com.tanxian.config;

import com.tanxian.entity.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

@Configuration
public class RedisConfig {

    /** 单节点 Redis 地址和端口 **/
    @Value("${spring.data.redis.host}")
    private String redisHost;
    @Value("${spring.data.redis.port}")
    private int redisPort;
    @Value("${spring.data.redis.password}")
    private String redisPassWord;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration standaloneConfig =
                new RedisStandaloneConfiguration(redisHost, redisPort);
        standaloneConfig.setPassword(RedisPassword.of(redisPassWord));

        // 配置连接池
        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(20);  // 最大连接数
        poolConfig.setMaxIdle(10);   // 最大空闲连接数
        poolConfig.setMinIdle(5);    // 最小空闲连接数
        poolConfig.setMaxWait(Duration.ofMillis(10000)); // 获取连接最大等待时间
        poolConfig.setTestOnBorrow(true);   // 获取连接时检测连接是否有效
        poolConfig.setTestOnReturn(true);   // 归还连接时检测连接是否有效
        poolConfig.setTestWhileIdle(true);  // 空闲时检测连接是否有效

        LettucePoolingClientConfiguration clientConfig =
                LettucePoolingClientConfiguration.builder()
                        .poolConfig(poolConfig)
                        .commandTimeout(Duration.ofSeconds(10))  // 命令超时时间
                        .shutdownTimeout(Duration.ofMillis(100)) // 关闭超时时间
                        .build();

        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        StringRedisTemplate tpl = new StringRedisTemplate();
        tpl.setConnectionFactory(factory);
        tpl.afterPropertiesSet();
        return tpl;
    }

    @Bean
    public RedisTemplate<String, ChatMessage> chatMessageRedisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, com.tanxian.entity.ChatMessage> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
