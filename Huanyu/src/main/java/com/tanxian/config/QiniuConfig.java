package com.tanxian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 七牛云配置类
 * 用于配置七牛云存储的相关参数
 */
@Configuration
@ConfigurationProperties(prefix = "qiniu")
public class QiniuConfig {

    /**
     * 七牛云Access Key
     */
    private String accessKey;

    /**
     * 七牛云Secret Key
     */
    private String secretKey;

    /**
     * 存储空间名称
     */
    private String bucketName;

    /**
     * 七牛云存储区域
     */
    private String region = "z0"; // 默认华东区域

    /**
     * 访问域名
     */
    private String domain;

    /**
     * 文件上传路径前缀
     */
    private String pathPrefix = "huanyu/avatar";

    /**
     * 文件大小限制（字节），默认10MB
     */
    private long maxFileSize = 10 * 1024 * 1024;

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }
}