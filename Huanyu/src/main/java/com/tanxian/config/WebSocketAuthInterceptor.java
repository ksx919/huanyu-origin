package com.tanxian.config;

import cn.hutool.core.util.StrUtil;
import com.tanxian.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * WebSocket握手拦截器：在建立连接时校验JWT并把用户信息写入会话属性
 * 支持通过Authorization: Bearer <token>、token头、以及查询参数?token= 传递JWT
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = null;

        // 1) Authorization: Bearer <token>
        HttpHeaders headers = request.getHeaders();
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 2) token头 或 查询参数?token=
        if (StrUtil.isBlank(token) && request instanceof ServletServerHttpRequest servletReq) {
            HttpServletRequest httpReq = servletReq.getServletRequest();
            token = StrUtil.blankToDefault(httpReq.getHeader("token"), httpReq.getParameter("token"));
        }

        if (StrUtil.isBlank(token)) {
            LOG.warn("WebSocket握手缺少JWT Token");
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        if (!JwtUtil.validateToken(token)) {
            LOG.warn("WebSocket握手JWT校验失败");
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        Map<String, Object> userInfo = JwtUtil.getUserInfoFromToken(token);
        Object uidObj = userInfo != null ? userInfo.get("userId") : null;
        Long uid = null;
        if (uidObj instanceof Number) {
            uid = ((Number) uidObj).longValue();
        } else if (uidObj instanceof String && !((String) uidObj).isEmpty()) {
            try { uid = Long.parseLong((String) uidObj); } catch (NumberFormatException ignored) {}
        }

        if (uid == null) {
            LOG.warn("WebSocket握手未解析到有效的用户ID");
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        // 写入会话属性，供WebSocketSession读取
        attributes.put("userId", uid);
        attributes.put("email", userInfo.get("email"));
        attributes.put("nickname", userInfo.get("nickname"));
        attributes.put("avatarUrl", userInfo.get("avatarUrl"));
        attributes.put("token", token);

        LOG.info("WebSocket握手成功，用户ID: {}，昵称: {}", uid, attributes.get("nickname"));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}