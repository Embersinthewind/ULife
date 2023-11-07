package com.ulife.controller;


import cn.hutool.core.bean.BeanUtil;
import com.ulife.dto.LoginFormDTO;
import com.ulife.dto.Result;
import com.ulife.dto.UserDTO;
import com.ulife.entity.User;
import com.ulife.entity.UserInfo;
import com.ulife.service.IUserInfoService;
import com.ulife.service.IUserService;
import com.ulife.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 易屿
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        // 实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 批量获取token的登录功能
     */
    // @PostMapping("/login")
    // public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
    //     String phone = loginForm.getPhone();
    //     String code = loginForm.getCode();
    //     if (phone == null) {
    //         return Result.fail("手机号为空！");
    //     }
    //     // if (code == null) {
    //     //     return Result.fail("验证码为空！");
    //     // }
    //     return userService.login(loginForm, session);
    // }

    /**
     * 登出功能
     *
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        // TODO 实现登出功能
        return userService.logout(request);
    }

    @GetMapping("/me")
    public Result me() {
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }


    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }


    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }
}
