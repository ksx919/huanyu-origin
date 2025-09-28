package com.tanxian.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResp {
    
    /**
     * 验证码唯一标识
     */
    private String captchaId;
    
    /**
     * 验证码图片Base64编码
     */
    private String captchaImage;
    
    /**
     * 过期时间（毫秒）
     */
    private Long expireTime;
}