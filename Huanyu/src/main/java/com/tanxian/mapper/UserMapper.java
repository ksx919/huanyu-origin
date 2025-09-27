package com.tanxian.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tanxian.entity.User;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 根据邮箱查找用户
     * @param email 邮箱
     * @return 用户信息
     */
    User findByEmail(@Param("email") String email);
    
    /**
     * 检查邮箱是否已存在
     * @param email 邮箱
     * @return 是否存在
     */
    boolean existsByEmail(@Param("email") String email);
    
    /**
     * 更新用户最后登录时间
     * @param userId 用户ID
     * @param lastLoginTime 最后登录时间
     * @return 更新行数
     */
    int updateLastLoginTime(@Param("userId") Long userId, @Param("lastLoginTime") LocalDateTime lastLoginTime);
}