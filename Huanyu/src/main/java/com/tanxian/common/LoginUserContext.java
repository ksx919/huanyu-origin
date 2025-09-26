package com.tanxian.common;

import com.tanxian.resp.LoginResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginUserContext {
    private static final Logger LOG = LoggerFactory.getLogger(LoginUserContext.class);

    private static ThreadLocal<LoginResp> user = new ThreadLocal<>();

    public static LoginResp getUser() {
        return user.get();
    }

    public static void setUser(LoginResp user) {
        LoginUserContext.user.set(user);
    }

    public static Long getId() {
        try {
            return user.get().getId();
        } catch (Exception e) {
            LOG.error("获取登录用户信息异常", e);
            throw e;
        }
    }
}
