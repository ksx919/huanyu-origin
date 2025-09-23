package com.tanxian.exception;

public enum BusinessExceptionEnum {
    CHAT_TYPE_ERROR("聊天类型传入错误"),
    AI_CHAT_TYPE_ERROR("AI聊天类型错误"),
    SYSTEM_MESSAGE_TEMPLATE_NOT_FOUND("系统消息模板未找到"),
    SYSTEM_MESSAGE_TEMPLATE_LOAD_FAILED("系统消息模板加载失败"),
    UNSUPPORTED_CHARACTER_TYPE("不支持的角色类型");

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
