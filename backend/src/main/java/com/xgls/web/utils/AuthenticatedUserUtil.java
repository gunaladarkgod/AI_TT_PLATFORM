package com.xgls.web.utils;

import cn.hutool.core.util.StrUtil;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.User;
import com.xgls.web.service.RedisService;
import com.xgls.web.service.UserService;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthenticatedUserUtil {
    private AuthenticatedUserUtil() {}

    /** 不信任客户端角色或旧 JWT 内角色，以数据库当前账号为准。 */
    public static User current(HttpServletRequest request, RedisService redis, UserService users) {
        String token = request.getHeader(CodeMap.X_ACCESS_TOKEN);
        if (StrUtil.isBlank(token)) return null;
        String realToken = redis.getJwtToken(token);
        if (realToken == null) return null;
        User principal = JwtUtils.verifyAndGetUser(realToken);
        if (principal == null || principal.getId() == null) return null;
        User user = users.getById(principal.getId());
        return user != null && CodeMap.USER_STATUS_OK.equals(user.getStatus()) ? user : null;
    }

    public static boolean isPlatformAdmin(User user) {
        return user != null && CodeMap.USER_TYPE_SYS.equals(user.getType());
    }
}
