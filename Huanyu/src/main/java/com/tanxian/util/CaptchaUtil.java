package com.tanxian.util;

import java.util.Random;
import java.util.UUID;

/**
 * 验证码工具类
 */
public class CaptchaUtil {
    
    private static final Random RANDOM = new Random();
    
    /**
     * 生成验证码唯一ID
     */
    public static String generateCaptchaId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成6位数字验证码
     */
    public static String generateEmailCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(RANDOM.nextInt(10));
        }
        return code.toString();
    }
    
    /**
     * 获取Redis中图形验证码的key
     */
    public static String getCaptchaRedisKey(String captchaId) {
        return "captcha:" + captchaId;
    }
    
    /**
     * 获取Redis中邮箱验证码的key
     */
    public static String getEmailCodeRedisKey(String email) {
        return "email_code:" + email;
    }
}