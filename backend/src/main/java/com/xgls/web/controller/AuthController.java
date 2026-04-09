package com.xgls.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.User;
import com.xgls.web.license.LicenseUtil;
import com.xgls.web.service.RedisService;
import com.xgls.web.service.UserService;
import com.xgls.web.utils.JwtUtils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/** 登录相关接口 */
@Tag(name = "登录管理")
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    UserService userService;
    @Autowired
    RedisService redisService;

    @Operation(summary = "用户登录", description = "登录接口")
    @PostMapping("login")
    public AjaxResult login(@Parameter(description = "用户名称", name = "username", required = true) String username,
            @Parameter(description = "用户密码", name = "pmd", required = true) String pmd) {

        if (true != LicenseUtil.LICENSE_STATUS.getStatus()) {
            return AjaxResult.error(LicenseUtil.LICENSE_STATUS.getErrMsg());
        }

        if (StrUtil.isBlank(username) || StrUtil.isBlank(pmd)) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        pmd = SecureUtil.md5(pmd + CodeMap.XGLS);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username).eq(User::getPmd, pmd);
        User user = userService.getOne(wrapper, false);
        if (user == null) {
            return AjaxResult.error(ErrorCode.ACCOUNT_PWD_WRONG);
        }
        String token = JwtUtils.generateTokenForUser(user);
        redisService.setJwtToken(token, token, JwtUtils.EXPIRE);
        // 保存用户和token的关系
        redisService.setUserJwt(user.getId(), token);
        return AjaxResult.success(token);
    }

    @Operation(summary = "退出登录", description = "退出登录")
    @PostMapping("logout")
    public AjaxResult logout(HttpServletRequest request) {
        String jwt = request.getHeader(CodeMap.X_ACCESS_TOKEN);
        if (StrUtil.isBlank(jwt)) {
            return AjaxResult.success();
        }
        String realToken = redisService.getJwtToken(jwt);
        if (realToken != null) {
            User user = JwtUtils.verifyAndGetUser(realToken);
            if (user != null && !user.isExpir()) {
                redisService.removeJwtToken(jwt);
                redisService.removeUserJwt(user.getId(), jwt);
            }
        }
        return AjaxResult.success();
    }

    /**
     * 鉴权失败的 默认返回接口
     * 
     * @return
     */
    @Operation(summary = "鉴权失败跳转接口")
    @GetMapping("unauthorized")
    public AjaxResult unauthorized() {
        return AjaxResult.error(ErrorCode.AUTH_FAILED);
    }

}
