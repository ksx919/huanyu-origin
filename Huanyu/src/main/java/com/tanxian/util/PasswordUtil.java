package com.tanxian.util;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 密码加密工具类
 * 提供MD5加密功能
 */
@Slf4j
public class PasswordUtil {

    /**
     * 盐值，用于增强密码安全性
     */
    private static final String SALT = "huanyu_password_salt_2025";

    /**
     * 对密码进行MD5加密
     * 
     * @param password 原始密码
     * @return 加密后的密码
     */
    public static String encryptPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        
        try {
            // 使用盐值增强安全性
            String saltedPassword = password + SALT;
            return DigestUtil.md5Hex(saltedPassword);
        } catch (Exception e) {
            log.error("密码加密失败", e);
            throw new RuntimeException("密码加密失败", e);
        }
    }

    /**
     * 验证密码是否正确
     * 
     * @param rawPassword 原始密码
     * @param encryptedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean verifyPassword(String rawPassword, String encryptedPassword) {
        if (rawPassword == null || encryptedPassword == null) {
            return false;
        }
        
        try {
            String encrypted = encryptPassword(rawPassword);
            return encrypted.equals(encryptedPassword);
        } catch (Exception e) {
            log.error("密码验证失败", e);
            return false;
        }
    }
}