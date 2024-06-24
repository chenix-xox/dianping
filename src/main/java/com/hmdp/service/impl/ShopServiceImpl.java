package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CodeUtils;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        // 返回
        return Result.ok(shop);
    }

    // 创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * @param id 商铺ID
     * @return com.hmdp.entity.Shop
     * @description 逻辑过期解决缓存击穿
     * - 需要缓存预热，提前把数据都缓存进去，就不存在缓存击穿问题，因为遇到redis没有的就字节返回为空了
     * @author chentianhai.cth
     * @date 2024/5/30 14:15
     */
    public Shop queryWithLogicalExpire(Long id) {
        /*
         *TODO
         * step1: 提交商铺id，从redis查询商铺缓存
         * step2: 判断缓存是否命中
         *     未命中：返回空
         *     命中：判断缓存是否过期
         *        未过期：返回数据
         *        过期：尝试获取互斥锁是否成功
         *          成功：开启独立线程，从数据库中查询，并缓存新数据，然后释放锁
         *          失败：返回数据（过期数据）
         */
        String key = CACHE_SHOP_KEY + id;
        // 提交商铺id，从Redis中查询商铺缓存
        String shopRedisResult = stringRedisTemplate.opsForValue().get(key);
        // 判断缓存是否命中 -> 未命中，直接返回为空
        if (StrUtil.isBlank(shopRedisResult)) {
//            return JSONUtil.toBean(shopRedisResult, Shop.class);
            return null;
        }

        // 命中 -> 判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopRedisResult, RedisData.class);
        Shop shop = JSONUtil.toBean(JSON.toJSONString(redisData.getData()), Shop.class);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 未过期，直接返回商铺信息
            System.out.println("未过期");
            return shop;
        }

        System.out.println("已过期");
        // 已过期
        if (!tryLock(LOCK_SHOP_KEY + id)) {
            // 获取锁失败，返回过期数据
            System.out.println("获取锁失败");
            return shop;
        }
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                // 重建缓存
                this.saveShop2Redis(id, 20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unLock(LOCK_SHOP_KEY + id);
            }
        });
        return shop;
    }

    /**
     * @param id 商铺ID
     * @return com.hmdp.entity.Shop
     * @description 互斥锁解决缓存击穿 -> 同时缓存了空值解决缓存穿透
     * @author chentianhai.cth
     * @date 2024/5/30 14:15
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // step1: 提交商铺id，从Redis中查询商铺缓存
        String shopRedisResult = stringRedisTemplate.opsForValue().get(key);
        // step2: 判断缓存是否命中 -> 2.1 命中，直接返回商铺信息
        if (StrUtil.isNotBlank(shopRedisResult)) {
            return JSONUtil.toBean(shopRedisResult, Shop.class);
        }

        // 不为null，说明redis中还是查到了，只是会被判断为blank，那就说明是空字符串
        // -> 是否命中空字符串，命中了空字符串就返回null
        if (shopRedisResult != null) {
            return null;
        }

        Shop shopDbResult = null;
        try {
            // 2.2 未命中 -> 尝试获取互斥锁 -> 是否获取成功
            if (!tryLock(LOCK_SHOP_KEY + id)) {
                // 失败: 休眠一会，从step2重新开始执行（此处重试整个方法）
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 成功: 根据ID查询数据库信息 -> 查询到的信息写入Redis -> 释放互斥锁
            shopDbResult = this.getById(id);
            if (shopDbResult == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(shopDbResult), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unLock(LOCK_SHOP_KEY + id);
        }
        return shopDbResult;
    }

    /**
     * @param id 商铺ID
     * @return com.hmdp.entity.Shop
     * @description 仅解决缓存穿透，根据ID查询商铺信息
     * @author chentianhai.cth
     * @date 2024/5/30 14:15
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 提交商铺id，从Redis中查询商铺缓存
        String shopRedisResult = stringRedisTemplate.opsForValue().get(key);
        // 判断缓存是否命中 -> 命中，直接返回商铺信息
        if (StrUtil.isNotBlank(shopRedisResult)) {
            return JSONUtil.toBean(shopRedisResult, Shop.class);
        }

        // 不为null，说明redis中还是查到了，只是会被判断为blank，那就说明是空字符串
        if (shopRedisResult != null) {
            return null;
        }

        // 未命中， 查数据库 -> 如果数据库中为空，缓存空值，解决缓存穿透，返回fail；
        Shop shopDbResult = this.getById(id);
        if (shopDbResult == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(shopDbResult), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shopDbResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShopById(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("店铺ID不能为空");
        }
        // 先更新
        updateById(shop);

        // 再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
        return Result.ok();
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

    /**
     * @param id            店铺ID
     * @param expireSeconds 基于当前时间增加的秒数，表明在多少秒后过期
     * @description 添加逻辑过时间，保存商铺信息到redis
     * @author chentianhai.cth
     * @date 2024/5/30 15:05
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        // 移除纳秒
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds).truncatedTo(ChronoUnit.SECONDS));
        // 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSON.toJSONString(redisData));
    }
}
