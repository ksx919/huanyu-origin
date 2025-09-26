package com.tanxian.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求对象
 */
@Data
public class RegisterReq {
    
    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /**
     * 邮箱验证码
     */
    @NotBlank(message = "邮箱验证码不能为空")
    @Size(min = 6, max = 6, message = "邮箱验证码必须为6位")
    private String emailCode;
    
    /**
     * MD5加密后的密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
    
    /**
     * 用户昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 50, message = "昵称长度必须在1-50个字符之间")
    private String nickname;
    
    /**
     * 用户头像URL（可选）
     */
    private String avatarUrl;
}