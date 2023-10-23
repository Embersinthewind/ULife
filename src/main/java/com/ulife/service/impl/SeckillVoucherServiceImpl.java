package com.ulife.service.impl;

import com.ulife.dto.Result;
import com.ulife.entity.SeckillVoucher;
import com.ulife.entity.VoucherOrder;
import com.ulife.mapper.SeckillVoucherMapper;
import com.ulife.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ulife.utils.RedisWorker;
import com.ulife.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {


}
