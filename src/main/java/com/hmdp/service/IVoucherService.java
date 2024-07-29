package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);

    /**
     * @description 用户抢购秒杀券
     * @param voucherId 优惠券ID
     * @return com.hmdp.dto.Result
     * @author chentianhai.cth
     * @date 2024/7/26 14:56
     */
    Result seckillVoucher(Long voucherId);
}
