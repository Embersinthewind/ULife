package com.ulife.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ulife.dto.Result;
import com.ulife.entity.Shop;
import com.ulife.mapper.ShopMapper;
import com.ulife.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.ulife.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商铺
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 1.设空值解决缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 2.互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
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
            boolean islock = tryLock(lockKey);
            // 4.2.判断是否获取成功
            if (!islock) {
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
     * 判断是否获取互斥锁
     *
     * @param lockKey
     * @return
     */
    @Override
    public boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 释放互斥锁
     *
     * @param lockKey
     */
    @Override
    public void unLock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
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

}
