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

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

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
        String key = CACHE_SHOP_KEY + id;
        // 1. 提交商铺ID
        // 2. 从Redis中查询商铺缓存
        // 3. 判断缓存是否命中
        //      3.1 未命中，查询数据库，写入Redis -> 没查到就返回404
        //      3.2 命中，直接返回商铺信息
        String shopRedisResult = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopRedisResult)) {
            // 命中
            System.out.println("before：" + shopRedisResult);
            Shop shop = JSONUtil.toBean(shopRedisResult, Shop.class);
            System.out.println("after：" + JSON.toJSONString(shop));
            return Result.ok(shop);
        }

        // 未命中， 查数据库
        //      -> 如果数据库中为空，直接返回fail；
        //      -> 不为空 就存入缓存，然后返回
        Shop shopDbResult = this.getById(id);
        if (shopDbResult == null) {
            return Result.fail("店铺不存在");
        }

        // 存入缓存
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(shopDbResult));
        stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shopDbResult);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShopById(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null){
            return Result.fail("店铺ID不能为空");
        }
        // 先更新
        updateById(shop);

        // 再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
        return Result.ok();
    }
}
