package com.ulife.service.impl;

import com.ulife.dto.Result;
import com.ulife.entity.SeckillVoucher;
import com.ulife.entity.VoucherOrder;
import com.ulife.mapper.VoucherOrderMapper;
import com.ulife.service.ISeckillVoucherService;
import com.ulife.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ulife.utils.RedisWorker;
import com.ulife.utils.UserHolder;
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
    private RedisWorker redisWorker;

    /**
     * 秒杀优惠券——超卖问题
     * @param voucherId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 多表操作，最好添加一个事务，防止出现错误，好回滚事务
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        //3.判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            //秒杀结束
            return Result.fail("秒杀已经结束！");
        }

        //4.判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }
        //5.扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!isSuccess) {
            return Result.fail("库存不足");
        }
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1订单ID
        long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        //6.2用户ID
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //6.3代金券ID
        voucherOrder.setVoucherId(voucherId);
        //将订单存储到数据库
        save(voucherOrder);
        //7.返回订单id
        return Result.ok(orderId);
    }
}
