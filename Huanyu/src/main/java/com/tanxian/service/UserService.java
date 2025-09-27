package com.tanxian.service;

import com.tanxian.req.LoginReq;
import com.tanxian.req.RegisterReq;
import com.tanxian.req.UpdatePasswordReq;
import com.tanxian.resp.LoginResp;
import com.tanxian.resp.RegisterResp;
import com.tanxian.resp.UpdateAvatarUrlResp;
import com.tanxian.resp.UpdateNickNameResp;
import jakarta.validation.Valid;

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

    /**
     * 更新用户密码
     * 
     * @param request 包含旧密码和新密码的请求对象
     * @return 登录响应对象，包含新的JWT token
     */
    LoginResp updatePassword(UpdatePasswordReq request);

    /**
     * 更新用户昵称
     * 
     * @param nickname 新昵称
     * @return 更新昵称响应对象，包含用户ID、新昵称和新的JWT token
     */
    UpdateNickNameResp updateNickName(String nickname);

    /**
     * 更新用户头像
     * 
     * @param avatarUrl 新头像URL
     * @return 更新头像响应对象，包含用户ID、新头像URL和新的JWT token
     */
    UpdateAvatarUrlResp updateAvatarUrl(String avatarUrl);
}
