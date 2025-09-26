package com.tanxian.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户登录响应对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResp {

    private Long id;
    /**
     * JWT Token
     */
    private String token;
    
    /**
     * 用户邮箱
     */
    private String email;
    
    /**
     * 用户昵称
     */
    private String nickname;
}