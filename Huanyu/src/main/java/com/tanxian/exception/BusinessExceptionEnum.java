package com.tanxian.exception;

public enum BusinessExceptionEnum {
    //聊天类型传入错误
    CHAT_TYPE_ERROR("聊天类型传入错误"),;

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
