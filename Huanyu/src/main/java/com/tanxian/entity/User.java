package com.tanxian.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    Long id;

    @TableField(value = "email")
    String email;

    @TableField(value = "password_hash")
    String password;

    @TableField(value = "nickname")
    String nickname;

    @TableField(value = "avatar_url")
    String avatarUrl;

    @TableField(value = "last_login_at",fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastLoginAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
