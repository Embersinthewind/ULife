package com.ulife.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulife.dto.Result;
import com.ulife.dto.UserDTO;
import com.ulife.entity.Blog;
import com.ulife.entity.User;
import com.ulife.mapper.BlogMapper;
import com.ulife.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ulife.service.IUserService;
import com.ulife.utils.SystemConstants;
import com.ulife.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ulife.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 易屿
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    //查询博客
    @Override
    public Result queryBlogById(Long id) {
        //1.通过id查询博客
        Blog blog = getById(id);
        //2.判断博客是否存在
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        //3.通过博客查询相关用户
        queryBlogUser(blog);
        //4.判断博客是否被当前用户点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    //判断博客是否被当前用户点赞
    private void isBlogLiked(Blog blog) {
        // 1.判断用户是否登录
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            //用户未登录，无需查询是否点赞
            blog.setIsLike(false);
            return;
        }
        //用户已登录
        // 2.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 3.判断当前用户是否点赞（判断redis中Zset集合是否有该成员）
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        blog.setIsLike(score != null);
    }

    //用户点赞博客操作
    @Transactional
    @Override
    public Result likeBlog(Long id) {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录！");
        }
        // 2.判断当前用户是否点赞（判断redis中是否有该成员）
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        // 3.用户未点赞
        if (score == null) {
            // 3.1 点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 3.2.保存用户到Redis的set集合 zadd key value score
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.用户已经点赞
            // 4.1 点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                // 4.2 把用户从redis的set集合中删除
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        //如果点赞用户列表为空，直接返回
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //2.解析出其中的用户id
        // 使用map方法将每个元素转换为对应的Long类型，并使用&oltéot方法将结果收集到一个List中
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3.根据用户id查询用户 WHERE id IN（5，1）ORDER BY FIELD（id，5，1）
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(" + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4. 返回
        return Result.ok(userDTOS);
    }

    //通过博客查询当前用户
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


}
