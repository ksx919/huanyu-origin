package com.tanxian.controller;

import com.tanxian.common.CommonResp;
import com.tanxian.resp.CaptchaResp;
import com.tanxian.req.EmailCodeReq;
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
        return new CommonResp<>(captchaResp);
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
        
        return new CommonResp<>(true, "邮箱验证码发送成功，请查收邮件", "发送成功");
    }
}
