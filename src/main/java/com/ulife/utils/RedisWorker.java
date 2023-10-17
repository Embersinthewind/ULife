package com.ulife.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一ID生成器
 */
@Component
public class RedisWorker {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 开始时间戳
     *
     * @return
     */
    public static final long BEGIN_TIMESTAMP = 1672531200L;
    public static final int COUNT_BITS = 32;

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2.生成序列号
        // 2.1获取当前日期,精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);
        // 3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime localDateTime = LocalDateTime.of(2023, 1, 1, 0, 0);
        long second = localDateTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }
}
