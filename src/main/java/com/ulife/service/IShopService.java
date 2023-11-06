package com.ulife.service;

import com.ulife.dto.Result;
import com.ulife.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 易屿
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryById(Long id);


    Shop queryWithPassThrough(Long id);

    Shop queryWithMutex(Long id);

    Shop queryWithLogiclExpire(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
