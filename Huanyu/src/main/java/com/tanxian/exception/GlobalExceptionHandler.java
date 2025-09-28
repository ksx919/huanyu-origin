package com.tanxian.exception;

import com.tanxian.common.CommonResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public CommonResp<Object> handleBusinessException(BusinessException e) {
        BusinessExceptionEnum exceptionEnum = e.getE();
        log.warn("业务异常：{}", exceptionEnum.getDesc());
        return new CommonResp<>(false, exceptionEnum.getDesc(), null);
    }

    /**
     * 处理参数校验异常 - @Valid 注解校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResp<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败：{}", errorMessage);
        return new CommonResp<>(false, "参数校验失败：" + errorMessage, null);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public CommonResp<Object> handleBindException(BindException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败：{}", errorMessage);
        return new CommonResp<>(false, "参数绑定失败：" + errorMessage, null);
    }

    /**
     * 处理约束违反异常 - @Validated 注解校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResp<Object> handleConstraintViolationException(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("约束违反：{}", errorMessage);
        return new CommonResp<>(false, "参数校验失败：" + errorMessage, null);
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public CommonResp<Object> handleException(Exception e) {
        log.error("系统异常", e);
        return new CommonResp<>(false, "系统内部错误，请稍后重试", null);
    }
}