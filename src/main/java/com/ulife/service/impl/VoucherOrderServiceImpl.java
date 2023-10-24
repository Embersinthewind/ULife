package com.ulife.service.impl;

import com.ulife.dto.Result;
import com.ulife.entity.SeckillVoucher;
import com.ulife.entity.VoucherOrder;
import com.ulife.mapper.VoucherOrderMapper;
import com.ulife.service.ISeckillVoucherService;
import com.ulife.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ulife.utils.RedisWorker;
import com.ulife.utils.SimpleRedisLock;
import com.ulife.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 易屿
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisWorker redisWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀优惠券——超卖问题
     *
     * @param voucherId
     * @return
     */
    @Override
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

        Long userId = UserHolder.getUser().getId();
        //获取分布式锁
        SimpleRedisLock redisLock = new SimpleRedisLock("order" + userId, stringRedisTemplate);

        boolean isLock = redisLock.tryLock(1200);

        //判断获取锁是否成功
        if (!isLock) {
            //redis已有该线程信息，获取锁失败，返回错误信息
            return Result.fail("不允许重复下单！");
        }

        // synchronized (userId.toString().intern()) {

        //获取锁成功
        try {
            // 获取到当前的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            redisLock.unLock();
        }
        // }

    }

    @Transactional(rollbackFor = Exception.class)
    public Result createVoucherOrder(Long voucherId) {
        //5.判断用户是否购买过
        //5.1获取用户
        Long userId = UserHolder.getUser().getId();
        //5.2根据ID去数据库查询用户
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //5.3判断用户是否已经购买过
        if (count > 0) {
            return Result.fail("用户已经购买过！");
        }

        //6.扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!isSuccess) {
            return Result.fail("库存不足");
        }
        //7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1订单ID
        long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2用户ID
        userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //7.3代金券ID
        voucherOrder.setVoucherId(voucherId);
        //将订单存储到数据库
        save(voucherOrder);
        //8.返回订单id
        return Result.ok(orderId);

    }
}
