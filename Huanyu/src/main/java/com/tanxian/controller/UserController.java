package com.tanxian.controller;

import com.tanxian.common.CommonResp;
import com.tanxian.req.LoginReq;
import com.tanxian.req.RegisterReq;
import com.tanxian.req.UpdatePasswordReq;
import com.tanxian.resp.CaptchaResp;
import com.tanxian.req.EmailCodeReq;
import com.tanxian.resp.LoginResp;
import com.tanxian.resp.RegisterResp;
import com.tanxian.service.CaptchaService;
import com.tanxian.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户相关接口")
@Slf4j
@Validated
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private CaptchaService captchaService;

    /**
     * 获取图形验证码
     */
    @GetMapping("/captcha")
    @Operation(summary = "获取图形验证码", description = "生成包含随机字符的图形验证码")
    public CommonResp<CaptchaResp> getCaptcha() {
        CaptchaResp captchaResp = captchaService.generateCaptcha();
        return CommonResp.success(captchaResp);
    }

    /**
     * 获取邮箱验证码
     */
    @PostMapping("/email-code")
    @Operation(summary = "获取邮箱验证码", description = "验证图形验证码后发送6位数字邮箱验证码")
    public CommonResp<String> getEmailCode(@Valid @RequestBody EmailCodeReq request) {
        captchaService.sendEmailCode(
            request.getEmail(), 
            request.getCaptchaId(), 
            request.getCaptchaCode()
        );
        
        return CommonResp.success("邮箱验证码发送成功，请查收邮件");
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "通过邮箱验证码注册新用户")
    public CommonResp<RegisterResp> register(@Valid @RequestBody RegisterReq request) {
        log.info("用户注册请求: email={}", request.getEmail());
        RegisterResp registerResp = userService.register(request);
        return CommonResp.success(registerResp);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "支持密码登录和邮箱验证码登录")
    public CommonResp<LoginResp> login(@Valid @RequestBody LoginReq request) {
        log.info("用户登录请求: email={}, loginType={}", request.getEmail(), request.getLoginType());
        LoginResp loginResp = userService.login(request);
        return CommonResp.success(loginResp);
    }

    @PostMapping("/update/password")
    @Operation(summary = "用户更新", description = "用户更新密码")
    public CommonResp<?> updatePassword(@Valid @RequestBody UpdatePasswordReq request) {
        log.info("用户更新请求: 旧密码={}, 新密码={}", request.getOldPassword(), request.getNewPassword());
        if(userService.updatePassword(request)){
            return CommonResp.success();
        }
        return CommonResp.error("更改密码失败");
    }

    @PostMapping("/update/nickname")
    @Operation(summary = "用户更新", description = "用户更新密码")
    public CommonResp<?> updateNickName(@Valid @RequestParam String nickname) {
        log.info("用户更新请求: 新昵称={}",nickname);
        if(userService.updateNickName(nickname)){
            return CommonResp.success();
        }
        return CommonResp.error("更改昵称失败");
    }
}
