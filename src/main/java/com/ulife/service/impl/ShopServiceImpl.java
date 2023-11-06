package com.ulife.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulife.dto.Result;
import com.ulife.entity.Shop;
import com.ulife.mapper.ShopMapper;
import com.ulife.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ulife.utils.CacheClient;
import com.ulife.utils.RedisData;
import com.ulife.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.ulife.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 易屿
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 查询商铺
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {

        // 1.设空值解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, CACHE_SHOP_TTL, TimeUnit.MINUTES, this::getById);

        // 2.互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);
        // if (shop == null) {
        //     return Result.fail("店铺不存在！");
        // }

        // 3.逻辑过期解决缓存击穿
        // Shop shop = cacheClient.queryWithLogiclExpire(CACHE_SHOP_KEY, LOCK_SHOP_KEY, id, Shop.class, CACHE_SHOP_TTL, TimeUnit.MINUTES, this::getById);
        // Shop shop=queryWithLogiclExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        //返回商铺信息
        return Result.ok(shop);
    }

    /**
     * 缓存穿透——设空值
     *
     * @param id
     * @return
     */
    @Override
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在,返回商铺信息
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否为空值
        if (shopJson != null) {
            return null;
        }
        // 4.不存在,根据id查询数据库
        Shop shop = getById(id);
        // 5.判断商铺是否存在
        if (shop == null) {
            // 6.商铺不存在,将空值存储在redis中
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        }
        // 7.商铺存在,写入redis,设置超时时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 8.返回商铺信息
        return shop;
    }

    /**
     * 缓存击穿——互斥锁
     *
     * @param id
     * @return
     */
    @Override
    public Shop queryWithMutex(Long id) {
        String key = LOCK_SHOP_KEY + id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在,返回商铺信息
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断命中的是否为空值
        if (shopJson != null) {
            return null;
        }
        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2.判断是否获取成功
            if (!isLock) {
                // 4.3.失败，则休眠重试
                Thread.sleep(50);
                queryWithMutex(id);
            }
            // 4.4.成功,根据id查询数据库
            shop = getById(id);
            // 模拟访问延迟
            Thread.sleep(200);
            // 5.判断商铺是否存在
            if (shop == null) {
                // 6.商铺不存在,将空值存储在redis中
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            }
            // 7.商铺存在,写入redis,设置超时时间
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 8.释放互斥锁
            unLock(lockKey);
        }
        // 9.返回商铺信息
        return shop;
    }

    /**
     * 为查询商铺缓存添加主动更新
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.根据id修改店铺
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }


    /**
     * 查询附近商铺
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (((List<?>) list).size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }



    // 建立线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 缓存击穿——逻辑过期
     *
     * @param id
     * @return
     */
    @Override
    public Shop queryWithLogiclExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 3.不存在,返回空
            return null;
        }

        // 4.存在,先将Json反序列化成对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Object data = redisData.getData();
        Shop shop = JSONUtil.toBean((JSONObject) data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，返回商铺信息
            return shop;
        }
        // 5.2.过期，需要进行缓存重建
        // 6.缓存重建
        // 6.1判断是否获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (!isLock) {
            // 6.2.获取到锁,开启独立线程,进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.3.未获取到锁,返回过期的商铺信息
        return shop;
    }


    /**
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.根据id获取商铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 判断是否获取互斥锁
     *
     * @param lockKey
     * @return
     */
    public boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 释放互斥锁
     *
     * @param lockKey
     */
    public void unLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }



}
