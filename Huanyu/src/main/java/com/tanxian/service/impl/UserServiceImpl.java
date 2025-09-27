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
import com.tanxian.resp.UpdateAvatarUrlResp;
import com.tanxian.resp.UpdateNickNameResp;
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
import java.time.Duration;

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

        // 4.1 将token写入Redis并设置TTL为JWT剩余时间
        try {
            String tokenKey = getUserTokenRedisKey(user.getId());
            long ttlMs = JwtUtil.getTokenRemainingTime(token);
            redisTemplate.opsForValue().set(tokenKey, token, Duration.ofMillis(Math.max(ttlMs, 1000)));
            log.info("用户登录token已写入Redis: key={}, ttlMs={}", tokenKey, ttlMs);
        } catch (Exception e) {
            log.error("写入登录token到Redis失败: userId={}", user.getId(), e);
        }
        
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
    public LoginResp updatePassword(UpdatePasswordReq request) {
        log.info("用户更新请求: 旧密码={}, 新密码={}", request.getOldPassword(), request.getNewPassword());

        // 1. 查找用户
        Long userId = LoginUserContext.getId();
        User user = userMapper.selectById(userId);
        if (ObjectUtil.isEmpty(user)) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }

        // 2. 校验旧密码是否正确
        if (PasswordUtil.verifyPassword(request.getOldPassword(), user.getPasswordHash())) {
            user.setPasswordHash(PasswordUtil.encryptPassword(request.getNewPassword()));
            userMapper.updateById(user);
        } else {
            throw new BusinessException(BusinessExceptionEnum.LOGIN_FAILED);
        }

        // 3. 更新最后登录时间
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateLastLoginTime(user.getId(), now);
        user.setLastLoginAt(now);

        log.info("用户更新密码成功: userId={}", user.getId());

        // 4. 轮换JWT：删除Redis中的旧token并生成新token写入Redis
        try {
            String tokenKey = getUserTokenRedisKey(user.getId());
            String oldToken = redisTemplate.opsForValue().get(tokenKey);
            if (oldToken != null) {
                redisTemplate.delete(tokenKey);
                log.info("更新密码删除旧token: key={}", tokenKey);
            }
            String newToken = JwtUtil.generateToken(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getAvatarUrl()
            );
            long ttlMs = JwtUtil.getTokenRemainingTime(newToken);
            redisTemplate.opsForValue().set(tokenKey, newToken, Duration.ofMillis(Math.max(ttlMs, 1000)));
            log.info("更新密码生成新token并写入Redis: key={}, ttlMs={}", tokenKey, ttlMs);

            // 返回新的登录响应
            return new LoginResp(
                    user.getId(),
                    newToken,
                    user.getEmail(),
                    user.getNickname(),
                    user.getAvatarUrl()
            );
        } catch (Exception e) {
            log.error("更新密码时轮换token失败: userId={}", user.getId(), e);
            // 失败情况下仍返回基本成功但不含新token（极端情况）
            return new LoginResp(
                    user.getId(),
                    null,
                    user.getEmail(),
                    user.getNickname(),
                    user.getAvatarUrl()
            );
        }
    }

    @Override
    @Transactional
    public UpdateNickNameResp updateNickName(String nickname) {
        log.info("用户更新请求: 新昵称={}",nickname);
        Long userId = LoginUserContext.getId();
        User user = userMapper.selectById(userId);
        if (ObjectUtil.isEmpty(user)) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }


        if(user.getNickname().equals(nickname)){
            log.error("新昵称不能和旧昵称相同");
            throw new BusinessException(BusinessExceptionEnum.NICKNAME_REPEAT);
        }
        user.setNickname(nickname);
        userMapper.updateById(user);

        // 删除旧token并生成新token
        String tokenKey = getUserTokenRedisKey(user.getId());
        try {
            String oldToken = redisTemplate.opsForValue().get(tokenKey);
            if (oldToken != null) {
                redisTemplate.delete(tokenKey);
                log.info("更新昵称删除旧token: key={}", tokenKey);
            }
        } catch (Exception e) {
            log.error("更新昵称删除旧token失败: userId={}", user.getId(), e);
        }

        String token = JwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl()
        );

        try {
            long ttlMs = JwtUtil.getTokenRemainingTime(token);
            redisTemplate.opsForValue().set(tokenKey, token, Duration.ofMillis(Math.max(ttlMs, 1000)));
            log.info("更新昵称生成新token并写入Redis: key={}, ttlMs={}", tokenKey, ttlMs);
        } catch (Exception e) {
            log.error("更新昵称写入新token失败: userId={}", user.getId(), e);
        }
        return new UpdateNickNameResp(
                user.getId(),
                nickname,
                token
        );

    }

    @Override
    @Transactional
    public UpdateAvatarUrlResp updateAvatarUrl(String avatarUrl) {
        log.info("用户更新请求: 新头像={}",avatarUrl);
        User user = userMapper.selectById(LoginUserContext.getId());
        user.setAvatarUrl(avatarUrl);
        userMapper.updateById(user);

        // 删除旧token并生成新token
        String tokenKey = getUserTokenRedisKey(user.getId());
        try {
            String oldToken = redisTemplate.opsForValue().get(tokenKey);
            if (oldToken != null) {
                redisTemplate.delete(tokenKey);
                log.info("更新头像删除旧token: key={}", tokenKey);
            }
        } catch (Exception e) {
            log.error("更新头像删除旧token失败: userId={}", user.getId(), e);
        }

        String token = JwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl()
        );

        try {
            long ttlMs = JwtUtil.getTokenRemainingTime(token);
            redisTemplate.opsForValue().set(tokenKey, token, Duration.ofMillis(Math.max(ttlMs, 1000)));
            log.info("更新头像生成新token并写入Redis: key={}, ttlMs={}", tokenKey, ttlMs);
        } catch (Exception e) {
            log.error("更新头像写入新token失败: userId={}", user.getId(), e);
        }
        return new UpdateAvatarUrlResp(
                user.getId(),
                avatarUrl,
                token
        );
    }

    private String getUserTokenRedisKey(Long userId) {
        return "auth:token:" + userId;
    }
}
