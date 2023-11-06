package com.ulife;


import com.ulife.entity.Shop;
import com.ulife.service.impl.ShopServiceImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import com.ulife.utils.CacheClient;
import com.ulife.utils.RedisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ulife.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.ulife.utils.RedisConstants.SHOP_GEO_KEY;


@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisWorker redisWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将1L商铺存入redis
     * @throws InterruptedException
     */
    @Test
    void testSaveShop2Redis() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    /**
     * 测试逻辑过期
     */
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


    /**
     * 将商铺地址（坐标x，y）存入redis
     */
    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> shopList = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                // 写法2
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}
