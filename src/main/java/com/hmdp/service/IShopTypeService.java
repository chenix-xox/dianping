package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * @Description: 查询门店类型
     * @Param:
     * @Return: com.hmdp.dto.Result
     * @Author: chentianhai.cth
     * @Date: 2024/5/23 16:19
     */
    Result queryList();

}
