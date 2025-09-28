package com.tanxian.controller;

import com.tanxian.common.CommonResp;
import com.tanxian.common.LoginUserContext;
import com.tanxian.resp.UpdateAvatarUrlResp;
import com.tanxian.service.UserService;
import com.tanxian.util.QiniuUploadUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 * 提供用户头像上传功能
 */
@RestController
@RequestMapping("/file")
@Tag(name = "文件上传", description = "文件上传相关接口")
public class FileUploadController {

    private static final Logger LOG = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private QiniuUploadUtil qiniuUploadUtil;

    @Autowired
    private UserService userService;

    /**
     * 上传用户头像
     *
     * @param file 头像文件
     * @return 上传结果
     */
    @PostMapping("/upload/avatar")
    @Operation(summary = "上传用户头像", description = "支持JPG、PNG等常见图片格式，文件大小不超过10MB")
    public CommonResp<UpdateAvatarUrlResp> uploadAvatar(
            @Parameter(description = "头像文件", required = true)
            @RequestParam("file") @NotNull MultipartFile file) {
        
        LOG.info("开始上传用户头像，文件名: {}, 文件大小: {} bytes", 
                file.getOriginalFilename(), file.getSize());

        try {
            // 记录旧头像文件键（来自当前JWT上下文）
            String oldFileKey = null;
            try {
                if (LoginUserContext.getUser() != null) {
                    oldFileKey = LoginUserContext.getUser().getAvatarUrl();
                }
            } catch (Exception ignore) {
                // 若上下文不可用，不影响上传流程
            }

            // 调用七牛云上传工具类
            String filePath = qiniuUploadUtil.uploadAvatar(file);

            // 数据库存储完整路径名，如 huanyu/avatar/1758945585361_*.png
            // 上传完成后，直接更新当前JWT用户的头像，并轮换Token
            UpdateAvatarUrlResp updateResp = userService.updateAvatarUrl(filePath);

            LOG.info("用户头像上传并更新成功，存储路径: {}, 原始文件名: {}", filePath, file.getOriginalFilename());

            // 尝试删除旧头像（若存在且与新头像不同）——容错处理，不影响主流程
            try {
                if (oldFileKey != null && !oldFileKey.isBlank() && !oldFileKey.equals(filePath)) {
                    boolean existed = false;
                    try {
                        existed = qiniuUploadUtil.exists(oldFileKey);
                    } catch (Exception e) {
                        // 存在性检查失败也不阻塞删除尝试
                        LOG.debug("检查旧头像是否存在失败，继续尝试删除: {}", oldFileKey);
                    }
                    if (!existed) {
                        LOG.info("旧头像不存在或已被删除: {}", oldFileKey);
                    } else {
                        boolean ok = qiniuUploadUtil.deleteFile(oldFileKey);
                        LOG.info("旧头像删除结果: key={}, ok={}", oldFileKey, ok);
                    }
                }
            } catch (Exception delEx) {
                LOG.warn("旧头像删除失败，不影响新头像使用: key={}, err={}", oldFileKey, delEx.getMessage());
            }

            return CommonResp.success(updateResp);
            
        } catch (Exception e) {
            LOG.error("用户头像上传失败: {}", e.getMessage(), e);
            return CommonResp.error("头像上传失败，请重试");
        }
    }

    @GetMapping("/private-url")
    @Operation(summary = "获取私有空间文件访问链接")
    public CommonResp<String> getPrivateUrl(
            @RequestParam(value = "expires", required = false) Long expires) {
        String fileName = LoginUserContext.getUser().getAvatarUrl();
        long exp = (expires == null || expires <= 0) ? 600L : expires; // 默认10分钟
        String url = qiniuUploadUtil.buildPrivateFileUrl(fileName, exp);
        return CommonResp.success(url);
    }

    @GetMapping("/private-url-by-key")
    @Operation(summary = "按文件键生成私有链接", description = "传入文件键（如huanyu/audio/xxx.wav）生成私有下载链接")
    public CommonResp<String> getPrivateUrlByKey(
            @RequestParam(value = "fileName") String fileName,
            @RequestParam(value = "expires", required = false) Long expires) {
        long exp = (expires == null || expires <= 0) ? 3600L : expires; // 默认1小时
        String url = qiniuUploadUtil.buildPrivateFileUrl(fileName, exp);
        return CommonResp.success(url);
    }

    /**
     * 获取文件上传限制信息
     *
     * @return 上传限制信息
     */
    @GetMapping("/upload/limits")
    @Operation(summary = "获取文件上传限制", description = "获取支持的文件格式和大小限制")
    public CommonResp<Object> getUploadLimits() {
        return CommonResp.success(new Object() {
            public final String[] supportedFormats = {"jpg", "jpeg", "png", "gif", "bmp", "webp"};
            public final long maxFileSize = 10 * 1024 * 1024; // 10MB
            public final String maxFileSizeDesc = "10MB";
        });
    }

    @GetMapping("/exists")
    @Operation(summary = "检查文件是否存在", description = "根据文件键检查七牛云是否存在该文件")
    public CommonResp<Boolean> exists(@RequestParam("fileName") String fileName) {
        try {
            boolean ok = qiniuUploadUtil.exists(fileName);
            return CommonResp.success(ok);
        } catch (Exception e) {
            LOG.error("检查文件存在失败", e);
            return CommonResp.success(false);
        }
    }
}