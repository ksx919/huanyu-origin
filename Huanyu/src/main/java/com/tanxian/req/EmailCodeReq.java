package com.tanxian.req;

import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱验证码请求DTO
 */
@Data
public class EmailCodeReq {
    
    /**
     * 邮箱地址
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /**
     * 图形验证码ID
     */
    @NotBlank(message = "验证码ID不能为空")
    private String captchaId;
    
    /**
     * 图形验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}