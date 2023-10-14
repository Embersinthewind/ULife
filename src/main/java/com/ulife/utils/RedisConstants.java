package com.ulife.utils;

public class RedisConstants {
    //登录验证码
    public static final String LOGIN_CODE_KEY = "login:code:";
    //验证码有效时间
    public static final Long LOGIN_CODE_TTL = 2L;
    //登录token的key
    public static final String LOGIN_USER_KEY = "login:token:";
    //登录token的有效时间
    public static final Long LOGIN_USER_TTL = 36000L;
    //缓存空值的有效时间
    public static final Long CACHE_NULL_TTL = 2L;
    //缓存商铺的有效时间
    public static final Long CACHE_SHOP_TTL = 30L;
    //缓存商铺的key
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    //缓存商铺类型的key
    public static final String CACHE_SHOP_LIST_KEY = "cache:shop:list:";
    //请求商铺互斥锁的key
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    //得到互斥锁的有效时间
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
