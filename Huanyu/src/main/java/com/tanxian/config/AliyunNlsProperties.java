package com.tanxian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aliyun.nls")
public class AliyunNlsProperties {
    private String appKey;
    private String accessKeyId;
    private String accessKeySecret;
    private String gatewayUrl;
    private String customizationId;
    private String vocabularyId;

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }

    public String getGatewayUrl() { return gatewayUrl; }
    public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }

    public String getCustomizationId() { return customizationId; }
    public void setCustomizationId(String customizationId) { this.customizationId = customizationId; }

    public String getVocabularyId() { return vocabularyId; }
    public void setVocabularyId(String vocabularyId) { this.vocabularyId = vocabularyId; }
}