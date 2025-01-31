package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * @description 用户抢购秒杀券
     * @param voucherId 优惠券ID
     * @return com.hmdp.dto.Result
     * @author chentianhai.cth
     * @date 2024/7/26 14:56
     */
    Result seckillVoucher(Long voucherId);

    /**
     * @description 代理使用 - 创建秒杀券订单方法（事务性方法）
     * @param voucherId 券ID
     * @return com.hmdp.dto.Result
     * @author chentianhai.cth
     * @date 2024/7/30 16:45
     */
    Result createVoucherOrder(Long voucherId);
}
