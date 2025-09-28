package com.tanxian.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户注册响应对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResp {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户邮箱
     */
    private String email;
    
    /**
     * 用户昵称
     */
    private String nickname;
    
    /**
     * 用户头像URL
     */
    private String avatarUrl;
    
    /**
     * 注册时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime createdAt;
}