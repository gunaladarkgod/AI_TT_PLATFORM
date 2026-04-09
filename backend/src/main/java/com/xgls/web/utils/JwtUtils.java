package com.xgls.web.utils;

import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.User;

/**
 * JWT 工具类
 * 
 */
public class JwtUtils {

    /**
     * 密钥
     */
    private static final byte[] KEY = SpringUtil.getProperty("sys.jwt-key", "xgls!213").getBytes();

    /**
     * 过期时间（秒） 24小时
     */
    public static final long EXPIRE = 3600*24;

    private JwtUtils() {
    }

    /**
     * 根据 userDto 生成 token
     *
     * @param dto 用户信息
     * @return token
     */
    public static String generateTokenForUser(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("type", user.getType());//1-系统管理员 3-普通用户
        return generateToken(map);
    }

    /**
     * 根据 map 生成 token 默认：HS265(HmacSHA256)算法
     *
     * @param map 携带数据
     * @return token
     */
    public static String generateToken(Map<String, Object> map) {
        JWT jwt = JWT.create();
        // 设置携带数据
        map.forEach(jwt::setPayload);
        // 设置密钥
        jwt.setKey(KEY);
        // 设置过期时间
        long now = System.currentTimeMillis();
        jwt.setExpiresAt(new Date(now + EXPIRE * 1000));
        // jwt.setIssuedAt(new Date(now));
        // jwt.setNotBefore(new Date(now));
        return jwt.sign();
    }

    /**
     * token 校验
     * 
     * @param token token
     * @return 是否通过校验
     */
    public static boolean verify(String token) {
        if (StringUtils.isBlank(token))
            return false;
        boolean flag = false;
        try {
            flag = JWT.of(token).setKey(KEY).verify();
        } catch (Exception e) {
            flag = false;
        }
        return flag;
    }

    /**
     * token 校验，并获取 userDto
     * 
     * @param token token
     * @return userDto
     */
    public static User verifyAndGetUser(String token) {
        if (!verify(token)) {
            return null;
        }
        // 解析数据
        JWT jwt = JWT.of(token);
        JSONObject jo = jwt.getPayload().getClaimsJson();
        Long id = jo.getLong("id");
        String username = jo.getStr("username");
        Date exp = jo.getDate(JWTPayload.EXPIRES_AT);
        Integer type = jo.getInt("type");

        // 返回用户信息
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setType(type);
        if (exp != null && exp.after(new Date())) {
            user.setExpir(false);
        } else {
            user.setExpir(true);
        }
        return user;
    }

    public static User getUserFromRequest(HttpServletRequest request) {
        String jwt = request.getHeader(CodeMap.X_ACCESS_TOKEN);
        if (jwt == null) {
            return null;
        }
        return verifyAndGetUser(jwt);
    }

}
