package com.ulife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.ulife.dto.Result;
import com.ulife.dto.UserDTO;
import com.ulife.entity.Follow;
import com.ulife.mapper.FollowMapper;
import com.ulife.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ulife.service.IUserService;
import com.ulife.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 易屿
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {

        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        String key = "follows:" + userId;

        //2.判断是否关注
        if (isFollow) {
            // 2.1已关注，保存数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);

            if (isSuccess) {
                //数据成功保存在数据库，将数据添加到redis
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 2.2未关注，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess) {
                //删除数据库数据成功，删除redis中的数据
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        //2.查询是否关注 select count（*）from tb_follow where user_id =？and follow_user_id =？
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取key
        //2.1当前登录用户所关注用户的key
        String currentUserFollowKey = "follows:" + userId;
        //2.2目标用户所关注用户的key
        String targetUserFollowKey = "follows:" + id;
        //3.共同关注：获取两个key中相同的元素
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(currentUserFollowKey, targetUserFollowKey);
        //4.判断是否有共同关注
        if (intersect == null || StringUtils.isEmpty(intersect)) {
            //4.1没有共同元素，返回
            return Result.ok("没有共同关注的好友");
        }
        //5.获取共同关注的id并存储为集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        //6.通过id集合查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
