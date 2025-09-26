package com.tanxian.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
 * 拦截器：Spring框架特有的，常用于登录校验，权限校验，请求日志打印
 */
@Component
public class UserInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(UserInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取header的token参数
        String token = request.getHeader("token");
        if (StrUtil.isNotBlank(token)) {
            LOG.info("获取用户登录token：{}", token);
            Map<String, Object> userInfo = JwtUtil.getUserInfoFromToken(token);
            LOG.info("当前登录用户：{}", userInfo.get("nickname"));

            JSONObject jsonObject = JSONUtil.parseObj(userInfo);
            LoginResp loginResp = jsonObject.toBean(LoginResp.class);
            loginResp.setToken(token);

            LoginUserContext.setUser(loginResp);
        }
        return true;
    }

}