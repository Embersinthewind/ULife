package com.ulife.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ulife.dto.Result;
import com.ulife.entity.ShopType;
import com.ulife.mapper.ShopTypeMapper;
import com.ulife.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.ulife.utils.RedisConstants.CACHE_SHOP_LIST_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    public StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String key = CACHE_SHOP_LIST_KEY;
        // 1.从redis查询商铺类型清单缓存
        String shopTypeJson = stringRedisTemplate.opsForList().leftPop(key);

        // 2.判断缓存是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 3.存在,直接返回
            List<ShopType> shopType = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopType);
        }

        // 4.不存在,查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        // 5.判断清单是否存在
        if (shopTypeList.isEmpty()) {
            // 6.清单不存在,返回错误
            return Result.fail("数据不存在");
        }

        // 7.清单存在,写入redis
        stringRedisTemplate.opsForList().leftPush(key, JSONUtil.toJsonStr(shopTypeList));
        // 8.返回清单信息
        return Result.ok(shopTypeList);
    }
}
