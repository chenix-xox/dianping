package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // step1. 查询秒杀优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // step2. 是否已经可以开抢？
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())
                || seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("不在秒杀时间范围内");
        }

        // step3. 库存是否充足？
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long uid = UserHolder.getUser().getId();
        synchronized (uid.toString().intern()) {
            IVoucherOrderService voucherOrderServiceProxy = (IVoucherOrderService) AopContext.currentProxy();
            return voucherOrderServiceProxy.createVoucherOrder(voucherId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(Long voucherId) {
        Long uid = UserHolder.getUser().getId();
        // step3.1 一人一单，判断该人是否已存在订单；新加的！
        int count = query().eq("user_id", uid)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            return Result.fail("该用户已经购买过该优惠券");
        }

        // step4. 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                // 只有stock大于0，才能更新成功
                .gt("stock", "0")
                .update();
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足");
        }

        // step5. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nexId("order"));
        voucherOrder.setUserId(uid);
        voucherOrder.setVoucherId(voucherId);
        voucherOrderService.save(voucherOrder);

        // step6. 返回订单ID
        return Result.ok(voucherOrder.getId());
    }
}
