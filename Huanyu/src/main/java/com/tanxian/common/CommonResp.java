package com.tanxian.common;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommonResp<T> {

    /**
     * 业务上的成功或失败
     */
    private boolean success = true;

    /**
     * 返回信息
     */
    private String message;

    /**
     * 返回泛型数据，自定义类型
     */
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

    /**
     * 成功响应
     *
     * @param data 返回数据
     * @param <T>  数据类型
     * @return CommonResp对象
     */
    public static <T> CommonResp<T> success(T data) {
        return new CommonResp<>(true, "操作成功", data);
    }

    /**
     * 成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return CommonResp对象
     */
    public static <T> CommonResp<T> success() {
        return new CommonResp<>(true, "操作成功", null);
    }

    /**
     * 失败响应
     *
     * @param message 错误信息
     * @param <T>     数据类型
     * @return CommonResp对象
     */
    public static <T> CommonResp<T> error(String message) {
        return new CommonResp<>(false, message, null);
    }

    /**
     * 失败响应
     *
     * @param message 错误信息
     * @param data    返回数据
     * @param <T>     数据类型
     * @return CommonResp对象
     */
    public static <T> CommonResp<T> error(String message, T data) {
        return new CommonResp<>(false, message, data);
    }

    @Override
    public String toString() {
        return "CommonResp{" + "success=" + success +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
