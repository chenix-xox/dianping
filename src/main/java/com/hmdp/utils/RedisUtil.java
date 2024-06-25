package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author chenix
 * @createTime 2024.06.24 17:40
 * @description
 */
@Slf4j
@Component
public class RedisUtil {
    // 创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * @param key  键
     * @param data 值
     * @param time TTL过期时间
     * @param unit 单位
     * @description 【TTL过期set】方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * @author chentianhai.cth
     * @date 2024/6/24 17:45
     */
    public void set(String key, Object data, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(data), time, unit);
    }

    /**
     * @param key  键
     * @param data 数据
     * @param time 基于当前时间，多久后过期（秒）？
     * @description 【逻辑过期set】方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     * @author chentianhai.cth
     * @date 2024/6/24 17:48
     */
    public <T> void setWithLogicalExpire(String key, T data, Long time, TimeUnit unit) {
        RedisData<T> redisData = new RedisData<>();
        redisData.setData(data);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(redisData));
    }

    /**
     * @param key 键
     * @param <T> 泛型
     * @return T
     * @description 方法3：根据指定的ky查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * @author chentianhai.cth
     * @date 2024/6/24 17:49
     */
    public <T> T get1(String key, Class<T> clazz) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            // 缓存空值，设置过期60s
            stringRedisTemplate.opsForValue().set(key, "", 60L, TimeUnit.SECONDS);
            return null;
        }
        return JSONUtil.toBean(json, clazz);
    }

    /**
     * @param keyPrefix  key的前缀
     * @param id         ID值
     * @param type       转换数据类型
     * @param dbFallback 从redis查询失败时，执行的方法 - 从数据库查询
     * @return R
     * @description 使用缓存空值的方法，解决缓存穿透
     * @author chentianhai.cth
     * @date 2024/6/24 18:32
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 从Redis中查询缓存json
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断缓存是否命中 -> 命中，直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 不为null，说明redis中还是查到了，只是会被判断为blank，那就说明是空字符串
        if (json != null) {
            return null;
        }

        // 未命中， 查数据库 -> 如果数据库中为空，缓存空值，解决缓存穿透，返回fail；
        R r = dbFallback.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        this.set(key, r, time, unit);
        return r;
    }


    /**
     * @param key 键
     * @param <T> 泛型
     * @return T
     * @description 方法4：根据指定的ky查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
     * @author chentianhai.cth
     * @date 2024/6/24 17:49
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
            Long time, TimeUnit unit, String lockKeyPrefix) {
        /*
         * step1: 提交商铺id，从redis查询商铺缓存
         * step2: 判断缓存是否命中
         *  - 未命中：返回空
         *  - 命中：判断缓存是否过期
         *     - 未过期：返回数据
         *     - 过期：尝试获取互斥锁是否成功
         *        - 成功：开启独立线程，从数据库中查询，并缓存新数据，然后释放锁
         *        - 失败：返回数据（过期数据）
         */
        String key = keyPrefix + id;
        // 提交商铺id，从Redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 判断缓存是否命中 -> 未命中，直接返回为空
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // 命中 -> 判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean(JSON.toJSONString(redisData.getData()), type);

        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 未过期，直接返回商铺信息
            System.out.println("未过期");
            return r;
        }

        System.out.println("已过期");
        // 已过期
        if (!tryLock(lockKeyPrefix + id)) {
            // 获取锁失败，返回过期数据
            System.out.println("获取锁失败");
            return r;
        }
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                // 重建缓存
                R rr = dbFallback.apply(id);
                this.setWithLogicalExpire(key, rr, time, unit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unLock(lockKeyPrefix + id);
            }
        });
        return r;
    }

    /**
     * @param key 锁的key
     * @return boolean
     * @description 获取互斥锁，10s过期
     * @author chentianhai.cth
     * @date 2024/5/30 14:03
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * @param key 钥匙放的锁key
     * @description 释放锁
     * @author chentianhai.cth
     * @date 2024/5/30 14:04
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
