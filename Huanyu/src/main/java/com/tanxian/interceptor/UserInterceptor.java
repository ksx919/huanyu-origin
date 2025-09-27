package com.tanxian.interceptor;

import cn.hutool.core.util.StrUtil;
import com.tanxian.common.LoginUserContext;
import com.tanxian.resp.LoginResp;
import com.tanxian.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 用户拦截器：拦截请求，解析JWT Token，将用户信息设置到上下文中
 */
@Component
public class UserInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(UserInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        LOG.debug("UserInterceptor处理请求: {}", requestURI);

        // 获取Authorization头中的JWT Token
        String authHeader = request.getHeader("Authorization");
        String token = null;
        
        // 支持两种Token传递方式：Authorization Bearer 和 直接的token头
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            // 兼容原有的token头方式
            token = request.getHeader("token");
        }
        
        if (StrUtil.isNotBlank(token)) {
            LOG.info("获取用户登录token：{}", token);
            
            // 验证Token有效性
            if (!JwtUtil.validateToken(token)) {
                LOG.warn("JWT Token验证失败: {}", token);
                // Token无效时返回403错误
                LoginUserContext.setUser(null);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"JWT Token无效或已过期\"}");
                return false;
            }

            // 从Token中获取用户信息
            Map<String, Object> userInfo = JwtUtil.getUserInfoFromToken(token);
            if (userInfo != null) {
                LOG.info("当前登录用户：{}", userInfo.get("nickname"));
                
                // 构建LoginResp对象
                LoginResp loginResp = new LoginResp();
                // 兼容不同类型的userId（Integer/Long/String）
                Object uidObj = userInfo.get("userId");
                Long uid = null;
                if (uidObj instanceof Number) {
                    uid = ((Number) uidObj).longValue();
                } else if (uidObj instanceof String && !((String) uidObj).isEmpty()) {
                    try {
                        uid = Long.parseLong((String) uidObj);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (uid == null) {
                    LOG.warn("JWT中缺少有效的用户ID");
                    LoginUserContext.setUser(null);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"JWT中缺少用户ID\"}");
                    return false;
                }
                loginResp.setId(uid);
                loginResp.setToken(token);
                loginResp.setEmail((String) userInfo.get("email"));
                loginResp.setNickname((String) userInfo.get("nickname"));
                loginResp.setAvatarUrl((String) userInfo.get("avatarUrl"));
                
                // 设置到线程上下文中
                LoginUserContext.setUser(loginResp);
                LOG.debug("用户信息已设置到上下文，用户ID: {}", loginResp.getId());
            } else {
                LOG.warn("无法从JWT Token获取用户信息: {}", token);
                LoginUserContext.setUser(null);
            }
        } else {
            LOG.warn("请求未携带JWT Token: {}", requestURI);
            // 清空上下文，确保线程安全
            LoginUserContext.setUser(null);
            // 返回403错误
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"未提供有效的JWT Token\"}");
            return false;
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求完成后清理ThreadLocal，防止内存泄漏
        LoginUserContext.setUser(null);
        LOG.debug("请求完成，已清理用户上下文");
    }
}