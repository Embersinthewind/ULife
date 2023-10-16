package com.ulife;


import com.ulife.entity.Shop;
import com.ulife.service.impl.ShopServiceImpl;

import com.ulife.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.ulife.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Test
    void testSaveShop2Redis() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testSetQueryWithLogiclExpire() {
        Shop shop = shopService.getById(1);
        cacheClient.setQueryWithLogiclExpire(CACHE_SHOP_KEY + 1L, shop, 20L, TimeUnit.SECONDS);

    }
}
