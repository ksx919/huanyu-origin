package com.tanxian.req;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 用户登录请求对象
 */
@Data
public class LoginReq {
    
    /**
     * 邮箱
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /**
     * 登录类型：0-密码登录，1-邮箱验证码登录
     */
    @NotNull(message = "登录类型不能为空")
    @Min(value = 0, message = "登录类型必须大于等于0")
    @Max(value = 1, message = "登录类型只能是0或1")
    private Short loginType;
    
    /**
     * 密码（密码登录时必填）
     */
    private String password;
    
    /**
     * 邮箱验证码（邮箱验证码登录时必填）
     */
    private String emailCode;
}