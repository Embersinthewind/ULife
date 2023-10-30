package com.ulife.service.impl;

import com.ulife.dto.Result;
import com.ulife.entity.VoucherOrder;
import com.ulife.mapper.VoucherOrderMapper;
import com.ulife.service.ISeckillVoucherService;
import com.ulife.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ulife.utils.RedisWorker;
import com.ulife.utils.SimpleRedisLock;
import com.ulife.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 易屿
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisWorker redisWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // @Resource
    // private RedissonClient redissonClient;

    //静态初始化lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //阻塞队列，用于存储订单
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    //建立线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //前置处理器
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHeader());
    }

    //异步下单
    public class VoucherOrderHeader implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    HeaderVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("订单异常", e);
                }
            }
        }
    }


    private void HeaderVoucherOrder(VoucherOrder voucherOrder) {
        //1.获取用户id
        Long userId = voucherOrder.getUserId();
        //2.创建分布式锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock("order" + userId, stringRedisTemplate);

        //3.获取分布式锁
        boolean isLock = redisLock.tryLock(1200);

        //4.判断获取锁是否成功
        if (!isLock) {
            //redis已有该线程信息，获取锁失败，返回错误信息
            log.error("不允许重复下单");
        }
        try {
            //5.返回创建的订单
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //6.释放锁
            redisLock.unLock();
        }

    }

    /**
     * lua脚本实现
     * 秒杀优惠券——超卖问题
     *
     * @param voucherId
     * @return
     */
    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();
        //2.执行lua脚本，返回结果
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());

        int flag = result.intValue();
        //3.对结果进行判断
        if (flag != 0) {
            // 3.1 返回结果不是0，创建订单失败
            return Result.fail(flag == 1 ? "库存不足" : "不能重复下单！");
        }

        //3.2 返回结果为0，创建订单
        //4.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //4.1订单ID
        long orderId = redisWorker.nextId("order");
        voucherOrder.setId(orderId);
        //4.2用户ID
        userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //4.3代金券ID
        voucherOrder.setVoucherId(voucherId);

        //5.将订单存在阻塞队列
        orderTasks.add(voucherOrder);

        //6. 获取到当前的代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //7.返回订单id
        return Result.ok(orderId);
    }

    /**
     * 创建优惠券订单
     *
     * @param voucherOrder
     */
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5.判断用户是否购买过
        //5.1获取用户
        Long userId = voucherOrder.getUserId();
        //5.2根据ID去数据库查询用户
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //5.3判断用户是否已经购买过
        if (count > 0) {
            log.error("不允许重复下单");
        }

        //6.扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!isSuccess) {
            log.error("库存不足");
        }
        //7.将订单存储到数据库
        save(voucherOrder);
    }







    /**
     * lua脚本 Stream消息队列
     * 秒杀优惠券——超卖问题
     *
     * @param voucherId
     * @return
     */
   /* private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();
        //4.1订单ID
        long orderId = redisWorker.nextId("order");
        //2.执行lua脚本，返回结果
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                 userId.toString());

        int flag = result.intValue();
        //3.对结果进行判断
        if (flag != 0) {
            // 3.1 返回结果不是0，创建订单失败
            return Result.fail(flag == 1 ? "库存不足" : "不能重复下单！");
        }

        //3.2 返回结果为0，创建订单
        //4.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

        voucherOrder.setId(orderId);
        //4.2用户ID
        userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //4.3代金券ID
        voucherOrder.setVoucherId(voucherId);

        //5.将订单存在阻塞队列
        orderTasks.add(voucherOrder);

        //6. 获取到当前的代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //7.返回订单id
        return Result.ok(orderId);
    }*/


    /**
     * 分布式锁
     * 秒杀优惠券——超卖问题
     *
     * @param voucherId
     * @return
     */
    /*@Override
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
        //创建分布式锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        // RLock lock = redissonClient.getLock("lock:order" + userId);

        //获取分布式锁
        boolean isLock = redisLock.tryLock(1200);
        //tryLock参数:(最大等待时间，自动释放锁时间，时间单位），无参情况下，(-1s,30s,s)
        // boolean isLock = lock.tryLock();

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
            // 将订单ID传入createVoucherOrder
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            redisLock.unLock();
            // lock.unlock();
        }
        // }

    }*/
}


