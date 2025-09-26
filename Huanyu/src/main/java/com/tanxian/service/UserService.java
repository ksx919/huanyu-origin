package com.tanxian.service;

import com.tanxian.req.LoginReq;
import com.tanxian.req.RegisterReq;
import com.tanxian.resp.LoginResp;
import com.tanxian.resp.RegisterResp;

public interface UserService {
    
    /**
     * 用户注册
     * @param registerReq 注册请求
     * @return 注册响应
     */
    RegisterResp register(RegisterReq registerReq);
    
    /**
     * 用户登录
     * @param loginReq 登录请求
     * @return 登录响应
     */
    LoginResp login(LoginReq loginReq);
    
    /**
     * 验证邮箱验证码
     * @param email 邮箱
     * @param emailCode 验证码
     * @return 是否验证成功
     */
    boolean verifyEmailCode(String email, String emailCode);
    
    /**
     * 验证用户密码
     * @param email 邮箱
     * @param password MD5加密后的密码
     * @return 是否验证成功
     */
    boolean verifyPassword(String email, String password);
}
