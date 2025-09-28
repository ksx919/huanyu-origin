package com.tanxian.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除图形验证码请求DTO
 */
@Data
public class DeleteCaptchaReq {

    /**
     * 图形验证码唯一标识
     */
    @NotBlank(message = "验证码ID不能为空")
    private String captchaId;
}