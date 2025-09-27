package com.tanxian.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.tanxian.common.LoginUserContext;
import com.tanxian.entity.User;
import com.tanxian.exception.BusinessException;
import com.tanxian.exception.BusinessExceptionEnum;
import com.tanxian.mapper.UserMapper;
import com.tanxian.req.LoginReq;
import com.tanxian.req.RegisterReq;
import com.tanxian.req.UpdatePasswordReq;
import com.tanxian.resp.LoginResp;
import com.tanxian.resp.RegisterResp;
import com.tanxian.service.UserService;
import com.tanxian.util.CaptchaUtil;
import com.tanxian.util.JwtUtil;
import com.tanxian.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Override
    @Transactional
    public RegisterResp register(RegisterReq registerReq) {
        log.info("用户注册请求: email={}", registerReq.getEmail());
        
        // 1. 验证邮箱验证码
        if (!verifyEmailCode(registerReq.getEmail(), registerReq.getEmailCode())) {
            throw new BusinessException(BusinessExceptionEnum.EMAIL_CODE_VERIFY_FAILED);
        }
        
        // 2. 检查邮箱是否已存在
        if (userMapper.existsByEmail(registerReq.getEmail())) {
            throw new BusinessException(BusinessExceptionEnum.USER_ALREADY_EXISTS);
        }
        
        // 4. 创建用户对象
        User user = new User();
        user.setEmail(registerReq.getEmail());
        user.setPasswordHash(PasswordUtil.encryptPassword(registerReq.getPassword()));
        user.setNickname(registerReq.getNickname());
        user.setAvatarUrl("1758945585361_f54ae96b267546fcb4fa2a4db957f98b.png");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        // 5. 保存用户
        userMapper.insert(user);
        
        log.info("用户注册成功: userId={}, email={}", user.getId(), user.getEmail());
        
        // 6. 返回注册响应
        return new RegisterResp(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getAvatarUrl(),
            user.getCreatedAt()
        );
    }
    
    @Override
    @Transactional
    public LoginResp login(LoginReq loginReq) {
        log.info("用户登录请求: email={}, loginType={}", loginReq.getEmail(), loginReq.getLoginType());

        // 1. 查找用户
        User user = userMapper.findByEmail(loginReq.getEmail());
        if (ObjectUtil.isEmpty(user)) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        
        // 2. 根据登录类型验证
        boolean verified;
        if (loginReq.getLoginType()==0) {
            // 密码登录
            if (loginReq.getPassword() == null || loginReq.getPassword().trim().isEmpty()) {
                throw new BusinessException(BusinessExceptionEnum.PASSWORD_REQUIRED);
            }
            verified = verifyPassword(loginReq.getEmail(), loginReq.getPassword());
        } else if (loginReq.getLoginType()==1) {
            // 邮箱验证码登录
            if (loginReq.getEmailCode() == null || loginReq.getEmailCode().trim().isEmpty()) {
                throw new BusinessException(BusinessExceptionEnum.EMAIL_CODE_REQUIRED);
            }
            verified = verifyEmailCode(loginReq.getEmail(), loginReq.getEmailCode());
        } else {
            throw new BusinessException(BusinessExceptionEnum.INVALID_LOGIN_TYPE);
        }
        
        if (!verified) {
            throw new BusinessException(BusinessExceptionEnum.LOGIN_FAILED);
        }
        
        // 3. 更新最后登录时间
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateLastLoginTime(user.getId(), now);
        user.setLastLoginAt(now);
        
        // 4. 生成JWT Token
        String token = JwtUtil.generateToken(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getAvatarUrl()
        );
        
        log.info("用户登录成功: userId={}, email={}", user.getId(), user.getEmail());
        
        // 5. 返回登录响应
        return new LoginResp(
            user.getId(),
            token,
            user.getEmail(),
            user.getNickname(),
            user.getAvatarUrl()
        );
    }
    
    @Override
    public boolean verifyEmailCode(String email, String emailCode) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(emailCode)) {
            return false;
        }

        try {
            String redisKey = CaptchaUtil.getEmailCodeRedisKey(email);
            String storedCode = redisTemplate.opsForValue().get(redisKey);

            if (storedCode == null) {
                log.warn("邮箱验证码不存在或已过期，邮箱: {}", email);
                return false;
            }

            if (!storedCode.equals(emailCode)) {
                log.warn("邮箱验证码不匹配，邮箱: {}", email);
                return false;
            }

            // 验证成功后删除验证码
            redisTemplate.delete(redisKey);
            log.info("邮箱验证码验证成功，邮箱: {}", email);
            return true;
        } catch (Exception e) {
            log.error("验证邮箱验证码时发生异常，邮箱: {}", email, e);
            return false;
        }
    }
    
    @Override
    public boolean verifyPassword(String email, String password) {
        try {
            User user = userMapper.findByEmail(email);
            if (ObjectUtil.isEmpty(user)) {
                return false;
            }
            // 验证MD5加密后的密码
            return PasswordUtil.verifyPassword(password, user.getPasswordHash());
        } catch (Exception e) {
            log.error("验证用户密码失败: email={}", email, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean updatePassword(UpdatePasswordReq request) {
        log.info("用户更新请求: 旧密码={}, 新密码={}", request.getOldPassword(), request.getNewPassword());

        // 1. 查找用户
        Long userId = LoginUserContext.getId();
        User user = userMapper.selectById(userId);
        if (ObjectUtil.isEmpty(user)) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }

        // 2. 校验旧密码密码是否正确
        if(user.getPasswordHash()==PasswordUtil.encryptPassword(request.getOldPassword())){
            user.setPasswordHash(PasswordUtil.encryptPassword(request.getNewPassword()));
        }
        else{
            return false;
        }

        // 3. 更新最后登录时间
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateLastLoginTime(user.getId(), now);
        user.setLastLoginAt(now);

        // 4. 生成JWT Token
        String token = JwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl()
        );

        log.info("用户更新密码成功: userId={}", user.getId());

        // 5. 返回登录响应
        return true;
    }

    @Override
    @Transactional
    public boolean updateNickName(String nickname) {
        log.info("用户更新请求: 新昵称={}",nickname);
        Long userId = LoginUserContext.getId();
        User user = userMapper.selectById(userId);
        if (ObjectUtil.isEmpty(user)) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }


        if(user.getNickname().equals(nickname)){
            return false;
        }
        user.setNickname(nickname);
        String token = JwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl()
        );
        return true;

    }
}
