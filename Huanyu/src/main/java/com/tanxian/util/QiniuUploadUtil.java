package com.tanxian.util;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.tanxian.config.QiniuConfig;
import com.tanxian.exception.BusinessException;
import com.tanxian.exception.BusinessExceptionEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 七牛云文件上传工具类
 * 支持用户头像上传到七牛云存储
 */
@Component
public class QiniuUploadUtil {

    private static final Logger LOG = LoggerFactory.getLogger(QiniuUploadUtil.class);

    @Autowired
    private QiniuConfig qiniuConfig;

    /**
     * 支持的图片格式
     */
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );

    /**
     * 上传用户头像到七牛云
     *
     * @param file 上传的文件
     * @return 文件访问URL
     */
    public String uploadAvatar(MultipartFile file) {
        // 1. 参数校验
        validateFile(file);

        // 2. 生成唯一文件名
        String fileName = generateUniqueFileName(file.getOriginalFilename());

        // 3. 构建七牛云配置
        Configuration cfg = buildConfiguration();
        UploadManager uploadManager = new UploadManager(cfg);

        // 4. 生成上传凭证
        Auth auth = Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
        String upToken = auth.uploadToken(qiniuConfig.getBucketName());

        try {
            // 5. 执行上传
            Response response = uploadManager.put(file.getBytes(), fileName, upToken);
            
            if (response.isOK()) {
                LOG.info("文件上传成功: {}", fileName);
                return buildFileUrl(fileName);
            } else {
                LOG.error("文件上传失败，响应码: {}, 响应体: {}", response.statusCode, response.bodyString());
                throw new BusinessException(BusinessExceptionEnum.QINIU_UPLOAD_FAILED);
            }
            
        } catch (QiniuException e) {
            LOG.error("七牛云上传异常: {}", e.getMessage(), e);
            throw new BusinessException(BusinessExceptionEnum.QINIU_UPLOAD_FAILED);
        } catch (IOException e) {
            LOG.error("文件读取异常: {}", e.getMessage(), e);
            throw new BusinessException(BusinessExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 上传ai合成音频到七牛云
     *
     * @param file 上传的文件
     * @return 文件访问URL
     */
    public String uploadAudio(MultipartFile file) {
        // 1. 参数校验
        validateFile(file);

        // 2. 生成唯一文件名（使用audio路径前缀）
        String fileName = generateUniqueFileName(file.getOriginalFilename(), "huanyu/audio/");

        // 3. 构建七牛云配置
        Configuration cfg = buildConfiguration();
        UploadManager uploadManager = new UploadManager(cfg);

        // 4. 生成上传凭证
        Auth auth = Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
        String upToken = auth.uploadToken(qiniuConfig.getBucketName());

        try {
            // 5. 执行上传
            Response response = uploadManager.put(file.getBytes(), fileName, upToken);

            if (response.isOK()) {
                LOG.info("文件上传成功: {}", fileName);
                return buildFileUrl(fileName);
            } else {
                LOG.error("文件上传失败，响应码: {}, 响应体: {}", response.statusCode, response.bodyString());
                throw new BusinessException(BusinessExceptionEnum.QINIU_UPLOAD_FAILED);
            }

        } catch (QiniuException e) {
            LOG.error("七牛云上传异常: {}", e.getMessage(), e);
            throw new BusinessException(BusinessExceptionEnum.QINIU_UPLOAD_FAILED);
        } catch (IOException e) {
            LOG.error("文件读取异常: {}", e.getMessage(), e);
            throw new BusinessException(BusinessExceptionEnum.FILE_READ_ERROR);
        }
    }

    /**
     * 直接上传AI合成的音频字节到七牛云（私有空间）
     * 不进行图片格式校验，统一按 wav 存储到 huanyu/audio/ 前缀下
     *
     * @param data 音频字节数据
     * @return 文件键（例如 huanyu/audio/xxxxx.wav），用于后续生成私有下载链接
     */
    public String uploadAudioBytes(byte[] data) {
        if (data == null || data.length == 0) {
            throw new BusinessException(BusinessExceptionEnum.FILE_UPLOAD_EMPTY);
        }

        // 生成唯一的音频文件名，固定使用 wav 扩展名与 huanyu/audio/ 路径前缀
        String fileName = generateUniqueFileName("audio.wav", "huanyu/audio/");

        // 构建七牛云配置与上传管理器
        Configuration cfg = buildConfiguration();
        UploadManager uploadManager = new UploadManager(cfg);

        // 生成上传凭证
        Auth auth = Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
        String upToken = auth.uploadToken(qiniuConfig.getBucketName());

        try {
            Response response = uploadManager.put(data, fileName, upToken);
            if (response.isOK()) {
                LOG.info("音频字节上传成功: {}", fileName);
                return buildFileUrl(fileName);
            } else {
                LOG.error("音频字节上传失败，响应码: {}, 响应体: {}", response.statusCode, response.bodyString());
                throw new BusinessException(BusinessExceptionEnum.QINIU_UPLOAD_FAILED);
            }
        } catch (QiniuException e) {
            LOG.error("七牛云上传异常: {}", e.getMessage(), e);
            throw new BusinessException(BusinessExceptionEnum.QINIU_UPLOAD_FAILED);
        }
    }

    /**
     * 指定文件键上传AI音频字节（覆盖写入）
     * @param data 音频字节
     * @param fileKey 期望的文件键（例如 huanyu/audio/<type>-<sha1>.wav）
     * @return 文件键（与传入一致）
     */
    public String uploadAudioBytesWithKey(byte[] data, String fileKey) {
        if (data == null || data.length == 0) {
            throw new BusinessException(BusinessExceptionEnum.FILE_UPLOAD_EMPTY);
        }
        if (fileKey == null || fileKey.isBlank()) {
            throw new BusinessException(BusinessExceptionEnum.PARAM_ERROR);
        }

        Configuration cfg = buildConfiguration();
        UploadManager uploadManager = new UploadManager(cfg);
        Auth auth = Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
        String upToken = auth.uploadToken(qiniuConfig.getBucketName());

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // 显式指定 MIME 类型为 audio/wav（部分环境下基于扩展名推断可能不稳定）
                Response response = uploadManager.put(data, fileKey, upToken, null, "audio/wav", false);
                if (response.isOK()) {
                    LOG.info("音频字节上传成功(指定键) attempt={} key={}", attempt, fileKey);
                    return buildFileUrl(fileKey);
                } else {
                    LOG.warn("音频字节上传失败(指定键) attempt={} code={} body={}", attempt, response.statusCode, response.bodyString());
                }
            } catch (QiniuException e) {
                LOG.warn("七牛云上传异常(指定键) attempt={} msg={}", attempt, e.getMessage());
                if (attempt == maxAttempts) {
                    LOG.error("七牛云上传最终失败(指定键): {}", e.getMessage(), e);
                    throw new BusinessException(BusinessExceptionEnum.QINIU_UPLOAD_FAILED);
                }
            }
        }
        throw new BusinessException(BusinessExceptionEnum.QINIU_UPLOAD_FAILED);
    }

    /**
     * 判断文件是否存在
     */
    public boolean exists(String fileKey) {
        try {
            com.qiniu.storage.BucketManager bucketManager = new com.qiniu.storage.BucketManager(
                    Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey()),
                    buildConfiguration()
            );
            com.qiniu.storage.model.FileInfo info = bucketManager.stat(qiniuConfig.getBucketName(), fileKey);
            return info != null;
        } catch (QiniuException e) {
            // 612: no such file or directory
            if (e.code() == 612) {
                return false;
            }
            LOG.error("检查文件存在异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 设置文件几天后自动删除（需七牛账户开通该能力）
     */
    public void setDeleteAfterDays(String fileKey, int days) {
        try {
            com.qiniu.storage.BucketManager bucketManager = new com.qiniu.storage.BucketManager(
                    Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey()),
                    buildConfiguration()
            );
            bucketManager.deleteAfterDays(qiniuConfig.getBucketName(), fileKey, days);
            LOG.info("设置文件自动删除: key={}, days={}", fileKey, days);
        } catch (QiniuException e) {
            LOG.warn("设置自动删除失败或不支持: {}", e.getMessage());
        }
    }

    /**
     * 验证上传文件
     *
     * @param file 上传的文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.FILE_UPLOAD_EMPTY);
        }

        // 检查文件大小
        if (file.getSize() > qiniuConfig.getMaxFileSize()) {
            LOG.warn("文件大小超出限制: {} bytes, 限制: {} bytes", file.getSize(), qiniuConfig.getMaxFileSize());
            throw new BusinessException(BusinessExceptionEnum.FILE_SIZE_EXCEEDED);
        }

        // 检查文件格式
        String originalFilename = file.getOriginalFilename();
        if (StrUtil.isBlank(originalFilename)) {
            throw new BusinessException(BusinessExceptionEnum.FILE_NAME_INVALID);
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            LOG.warn("不支持的文件格式: {}", extension);
            throw new BusinessException(BusinessExceptionEnum.FILE_FORMAT_NOT_SUPPORTED);
        }
    }

    /**
     * 生成唯一文件名
     *
     * @param originalFilename 原始文件名
     * @return 唯一文件名
     */
    private String generateUniqueFileName(String originalFilename) {
        return generateUniqueFileName(originalFilename, qiniuConfig.getPathPrefix());
    }
    
    /**
     * 生成唯一文件名
     *
     * @param originalFilename 原始文件名
     * @param pathPrefix 路径前缀
     * @return 唯一文件名
     */
    private String generateUniqueFileName(String originalFilename, String pathPrefix) {
        String extension = getFileExtension(originalFilename);
        String uniqueId = IdUtil.simpleUUID();
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        return pathPrefix + timestamp + "_" + uniqueId + "." + extension;
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名
     */
    private String getFileExtension(String filename) {
        if (StrUtil.isBlank(filename)) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 构建七牛云配置
     *
     * @return Configuration对象
     */
    private Configuration buildConfiguration() {
        Configuration cfg = new Configuration();

        // 使用自动区域识别，这是推荐的现代化配置方式
        // 七牛云SDK会根据存储空间自动识别区域，无需手动指定
        cfg.region = Region.autoRegion();
        
        return cfg;
    }

    /**
     * 构建文件访问URL
     *
     * @param fileName 文件名
     * @return 完整的访问URL
     */
    private String buildFileUrl(String fileName) {
        String domain = qiniuConfig.getDomain();
        if (StrUtil.isBlank(domain)) {
            LOG.error("七牛云访问域名未配置");
            throw new BusinessException(BusinessExceptionEnum.QINIU_CONFIG_ERROR);
        }
        
        return fileName;
    }

    /**
     * 删除七牛云文件
     *
     * @param fileName 文件名
     * @return 是否删除成功
     */
    public boolean deleteFile(String fileName) {
        try {
            Configuration cfg = buildConfiguration();
            Auth auth = Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
            
            com.qiniu.storage.BucketManager bucketManager = new com.qiniu.storage.BucketManager(auth, cfg);
            Response response = bucketManager.delete(qiniuConfig.getBucketName(), fileName);
            
            if (response.isOK()) {
                LOG.info("文件删除成功: {}", fileName);
                return true;
            } else {
                LOG.error("文件删除失败，响应码: {}, 响应体: {}", response.statusCode, response.bodyString());
                throw new BusinessException(BusinessExceptionEnum.QINIU_DELETE_FAILED);
            }
            
        } catch (QiniuException e) {
            LOG.error("七牛云删除文件异常: {}", e.getMessage(), e);
            throw new BusinessException(BusinessExceptionEnum.QINIU_DELETE_FAILED);
        }
    }

    public String buildPrivateFileUrl(String fileName, long expireSeconds) {
        String domain = qiniuConfig.getDomain();
        if (StrUtil.isBlank(domain)) {
            throw new BusinessException(BusinessExceptionEnum.QINIU_CONFIG_ERROR);
        }
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "http://" + domain;
        }
        if (!domain.endsWith("/")) {
            domain += "/";
        }
        String baseUrl = domain + fileName;
        Auth auth = Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
        return auth.privateDownloadUrl(baseUrl, expireSeconds);
    }
}