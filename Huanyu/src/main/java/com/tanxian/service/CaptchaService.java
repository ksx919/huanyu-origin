package com.tanxian.service;

import com.tanxian.resp.CaptchaResp;

/**
 * 验证码服务接口
 */
public interface CaptchaService {
    
    /**
     * 生成图形验证码
     * 
     * @return 验证码响应
     */
    CaptchaResp generateCaptcha();
    
    /**
     * 验证图形验证码
     * 
     * @param captchaId 验证码ID
     * @param captchaCode 验证码
     * @return 是否验证通过
     */
    boolean verifyCaptcha(String captchaId, String captchaCode);
    
    /**
     * 发送邮箱验证码
     * 
     * @param email 邮箱地址
     * @param captchaId 图形验证码ID
     * @param captchaCode 图形验证码
     * @return 是否发送成功
     */
    boolean sendEmailCode(String email, String captchaId, String captchaCode);
}