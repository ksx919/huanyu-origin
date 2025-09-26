package com.tanxian.common;

import lombok.Getter;
import lombok.Setter;

@Setter
public class CommonResp<T> {

    /**
     * 业务上的成功或失败
     */
    private boolean success = true;

    /**
     * 返回信息
     */
    @Getter
    private String message;

    /**
     * 返回泛型数据，自定义类型
     */
    @Getter
    private T data;

    public CommonResp() {
    }

    public CommonResp(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public CommonResp(T data) {
        this.data = data;
    }

    public boolean getSuccess() {
        return success;
    }

    @Override
    public String toString() {
        return "CommonResp{" + "success=" + success +
                ", message='" + message + '\'' +
                ", content=" + data +
                '}';
    }
}
