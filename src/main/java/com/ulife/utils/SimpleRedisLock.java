package com.ulife.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;


import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    //业务名称
    private final String name;
    //redis
    private final StringRedisTemplate stringRedisTemplate;
    //锁的前缀
    public static final String KEY_PREFIX = "lock:";
    //用户标识前缀
    public static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取锁
     *
     * @param timeoutSec
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        // 1.获取当前线程的id并和用户标识拼接
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 2.获取锁（判断当前redis中某项业务是否存在该线程）
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    /**
     * 释放锁
     */
    @Override
    public void unLock() {
        //1.判断是否是当前用户
        //1.1 获取当前线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //1.2 获取redis中线程标识
        String redisId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //2.判断标识是否一致
        if (redisId.equals(threadId)) {
            //一致，则是相同线程，释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
