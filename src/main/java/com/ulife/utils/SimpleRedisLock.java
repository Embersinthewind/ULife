package com.ulife.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;


import java.util.Collections;
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
    //静态初始化lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unLock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

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
     * lua脚本
     */
    @Override
    public void unLock() {
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }


    /**
     * 释放锁
     */
    // @Override
    // public void unLock() {
    //     //1.判断是否是当前用户
    //     //1.1 获取当前线程标识
    //     String threadId = ID_PREFIX + Thread.currentThread().getId();
    //     //1.2 获取redis中线程标识
    //     String redisId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    //     //2.判断标识是否一致
    //     if (redisId.equals(threadId)) {
    //         //一致，则是相同线程，释放锁
    //         stringRedisTemplate.delete(KEY_PREFIX + name);
    //     }
    // }

}
