package com.tanxian.service;

/**
 * 邮件发送服务接口
 */
public interface EmailService {
    
    /**
     * 发送邮箱验证码
     * 
     * @param email 邮箱地址
     * @param code 验证码
     * @return 是否发送成功
     */
    boolean sendEmailCode(String email, String code);
}