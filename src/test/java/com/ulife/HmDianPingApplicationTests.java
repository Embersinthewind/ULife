package com.ulife;


import com.ulife.entity.Shop;
import com.ulife.service.impl.ShopServiceImpl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import com.ulife.utils.CacheClient;
import com.ulife.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.annotation.Resource;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.ulife.utils.RedisConstants.CACHE_SHOP_KEY;


@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisWorker redisWorker;

    @Test
    void testSaveShop2Redis() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testSetQueryWithLogiclExpire() {
        Shop shop = shopService.getById(1);
        cacheClient.setQueryWithLogiclExpire(CACHE_SHOP_KEY + 1L, shop, 20L, TimeUnit.SECONDS);

    }

    //建立线程池
    private static final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testRedisWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(100);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisWorker.nextId("order");
                System.out.println("id:" + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("执行时间：" + (end - begin));
    }
}
