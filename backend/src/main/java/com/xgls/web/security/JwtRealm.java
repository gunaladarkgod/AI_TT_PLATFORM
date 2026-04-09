package com.xgls.web.security;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.User;
import com.xgls.web.license.LicenseUtil;
import com.xgls.web.service.RedisService;
import com.xgls.web.utils.JwtUtils;
import com.xgls.web.utils.SessionUtil;

@Component
public class JwtRealm extends AuthorizingRealm {

    @Autowired
    RedisService redisService;

    // 让shiro支持我们自定义的token，即如果传入的token时JWTToken则放行
    // 必须重写不然shiro会报错
    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    // 检验权限时调用
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principalCollection) {
        Integer roleId = SessionUtil.getCurRoleId();
        if (roleId != null) {
            String role = CodeMap.RoleMap.get(roleId);
            if (role != null) {
                SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
                info.addRole(role);
                if (role.equals(CodeMap.ROLE_SYS)) {// 系统管理员具备管理员的权限
                    info.addRole(CodeMap.ROLE_ADMIN);
                }
                return info;
            }
        }
        return null;
    }

    // 认证和鉴权时调用
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken)
            throws AuthenticationException {
        if (true != LicenseUtil.LICENSE_STATUS.getStatus()) {
            throw new AuthenticationException("License 无效");
        }

        String token = (String) authenticationToken.getCredentials();// 重写了该类，实际上返回的是token

        // 先检查token是否存在
        String realToken = redisService.getJwtToken(token);
        if (realToken == null) {// token 已过期
            throw new AuthenticationException("token 已过期");
        }
        User user = JwtUtils.verifyAndGetUser(realToken);
        if (user == null) {
            throw new AuthenticationException("token 验证失败");
        }
        if (user.isExpir()) {
            // 过期了,刷新token
            String tokenRefresh = JwtUtils.generateTokenForUser(user);
            redisService.setJwtToken(token, tokenRefresh, JwtUtils.EXPIRE);
        } else {
            // 更新redis 时间
            redisService.setJwtToken(token, realToken, JwtUtils.EXPIRE);
        }
        SecurityUtils.getSubject().getSession().setAttribute(CodeMap.CUR_USER, user);
        return new SimpleAuthenticationInfo(token, token, this.getName());
    }
}
