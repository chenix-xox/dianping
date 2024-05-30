package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CodeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
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

    /**
     *TODO
     * step1: 提交商铺id，从Redis中查询商铺缓存
     * step2: 判断缓存是否命中
     *  2.1 命中 -> 返回数据
     *  2.2 未命中 -> 尝试获取互斥锁 -> 是否获取成功
     *      - 失败: 休眠一会，从step2重新开始执行
     *      - 成功: 根据ID查询数据库信息 -> 查询到的信息写入Redis -> 释放互斥锁
     */
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 提交商铺id，从Redis中查询商铺缓存
        String shopRedisResult = stringRedisTemplate.opsForValue().get(key);
        // 判断缓存是否命中 -> 命中，直接返回商铺信息
        if (StrUtil.isNotBlank(shopRedisResult)) {
            return Result.ok(JSONUtil.toBean(shopRedisResult, Shop.class));
        }

        // 不为null，说明redis中还是查到了，只是会被判断为blank，那就说明是空字符串
        if (shopRedisResult != null) {
            return Result.fail("店铺不存在");
        }

        // 未命中， 查数据库 -> 如果数据库中为空，缓存空值，解决缓存穿透，返回fail；
        Shop shopDbResult = this.getById(id);
        if (shopDbResult == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }

        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(shopDbResult));
        stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shopDbResult);
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
     * @description 获取互斥锁，10s过期
     * @param key 锁的key
     * @return boolean
     * @author chentianhai.cth
     * @date 2024/5/30 14:03
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * @description 释放锁
     * @param key 钥匙放的锁key
     * @author chentianhai.cth
     * @date 2024/5/30 14:04
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
