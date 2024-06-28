package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author chenix
 * @createTime 2024.06.27 10:59
 * @description 全局唯一ID生成器
 */
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nexId(String keyPrefix){
        // ID组成：时间戳 + 序列号
        // step1. 生成时间戳 —— 用当前时间-起始时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // step2. 生成序列号，必须是自增长的
        // - step2.1 获取当前时间
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // - step2.2 自增长的序列号生成
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 拼接返回 - long类型为32位，将时间戳左移32位，与count拼接
        return timestamp << 32 | count;
    }
}
