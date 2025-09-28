package com.tanxian.config;

import com.tanxian.interceptor.UserInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private UserInterceptor userInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(userInterceptor)
                .excludePathPatterns(
                        // 用户认证相关接口
                        "/user/captcha",
                        "/user/captcha/delete",
                        "/user/email-code", 
                        "/user/register",
                        "/user/login",
                        
                        // 测试接口
                        "/test/**",
                        
                        // WebSocket连接
                        "/ws-audio",
                        
                        // API文档相关
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/doc.html",
                        "/webjars/**",
                        
                        // 静态资源
                        "/static/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        
                        // Spring Boot Actuator健康检查
                        "/actuator/**",
                        
                        // 错误页面
                        "/error"
                );
    }

    @Override
    public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
