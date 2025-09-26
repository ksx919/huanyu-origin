package com.tanxian.exception;

public enum BusinessExceptionEnum {
    CHAT_TYPE_ERROR("聊天类型传入错误"),
    SYSTEM_MESSAGE_TEMPLATE_NOT_FOUND("系统消息模板未找到"),
    SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED("系统消息模板加载失败"),
    UNSUPPORTED_CHARACTER_TYPE("不支持的角色类型"),
    
    // 验证码相关异常
    CAPTCHA_GENERATE_FAILED("验证码生成失败"),
    CAPTCHA_VERIFY_FAILED("验证码验证失败"),
    CAPTCHA_NOT_FOUND("验证码不存在"),
    EMAIL_SEND_FAILED("邮件发送失败"),
    EMAIL_CODE_SEND_FAILED("邮箱验证码发送失败"),
    EMAIL_CODE_VERIFY_FAILED("邮箱验证码验证失败"),
    
    // 用户认证相关异常
    USER_ALREADY_EXISTS("用户已存在"),
    USER_NOT_FOUND("用户不存在"),
    PASSWORD_REQUIRED("密码不能为空"),
    EMAIL_CODE_REQUIRED("邮箱验证码不能为空"),
    INVALID_LOGIN_TYPE("无效的登录类型"),
    LOGIN_FAILED("登录失败"),
    
    // JWT Token相关异常
    TOKEN_REQUIRED("请求缺少JWT Token"),
    TOKEN_INVALID("JWT Token无效或已过期");

    BusinessExceptionEnum(String desc) {
        this.desc = desc;
    }

    private String desc;

    @Override
    public String toString() {
        return "BusinessExceptionEnum{" +
                "desc='" + desc + '\'' +
                '}';
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
