package com.ulife.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.ulife.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setQueryWithLogiclExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), time, unit);
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit unit, Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在,返回商铺信息
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否为空值
        if (json != null) {
            return null;
        }
        // 4.不存在,根据id查询数据库
        R r = dbFallback.apply(id);
        // 5.判断商铺是否存在
        if (r == null) {
            // 6.商铺不存在,将空值存储在redis中
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        }
        // 7.商铺存在,写入redis,设置超时时间
        // stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        this.set(key, r, time, unit);
        // 8.返回商铺信息
        return r;
    }


    // 建立线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 缓存击穿——逻辑过期
    public <R, ID> R queryWithLogiclExpire(String keyPrefix, String lockKeyPrefix, ID id, Class<R> type, Long time, TimeUnit unit, Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断缓存是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在,返回空
            return null;
        }

        // 4.存在,先将Json反序列化成对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Object data = redisData.getData();
        R r = JSONUtil.toBean((JSONObject) data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，返回商铺信息
            return r;
        }
        // 5.2.过期，需要进行缓存重建
        // 6.缓存重建
        // 6.1判断是否获取锁
        String lockKey = lockKeyPrefix + id;
        boolean isLock = tryLock(lockKey);
        if (!isLock) {
            // 6.2.获取到锁,开启独立线程,进行缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入Redis
                    this.setQueryWithLogiclExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.3.未获取到锁,返回过期的商铺信息
        return r;
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
