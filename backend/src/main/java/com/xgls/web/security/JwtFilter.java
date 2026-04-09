package com.xgls.web.security;

import java.io.IOException;

import org.apache.shiro.web.filter.AccessControlFilter;
import org.springframework.stereotype.Component;

import com.xgls.web.base.CodeMap;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends AccessControlFilter {
    /*
     * 1. 返回true，shiro就直接允许访问url
     * 2. 返回false，shiro才会根据onAccessDenied的方法的返回值决定是否允许访问url
     */
    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue)
            throws Exception {
        return false;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest servletRequest, ServletResponse servletResponse) throws Exception {
        // 在Header中放一个Authorization，值就是对应的Token
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String token = request.getHeader(CodeMap.X_ACCESS_TOKEN);
        if (StrUtil.isBlank(token)) {
            onLoginFail(servletResponse);
            return false;
        }

        JwtToken jwtToken = new JwtToken(token);
        try {
            // 委托 realm 进行登录认证
            // 所以这个地方最终还是调用JwtRealm进行的认证
            getSubject(servletRequest, servletResponse).login(jwtToken);
        } catch (Exception e) {
            // log.error(e.getMessage());
            // 调用下面的方法向客户端返回错误信息
            onLoginFail(servletResponse);
            return false;
        }
        // 执行方法中没有抛出异常就表示登录成功
        return true;
    }

    // 登录失败时默认返回 401 状态码
    private void onLoginFail(ServletResponse response) throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.getWriter().write("Authentication failed");
    }
}
