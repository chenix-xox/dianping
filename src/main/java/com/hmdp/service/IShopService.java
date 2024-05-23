package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * @Description: 根据ID获取商铺信息
     * @Param: id
     * @Return: com.hmdp.dto.Result
     * @Author: chentianhai.cth
     * @Date: 2024/5/23 14:02
     */
    Result queryById(Long id);
}
