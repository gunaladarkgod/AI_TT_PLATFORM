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

    /** 独立验证登录凭据，兼容开发模式关闭 Shiro 全局过滤的配置。 */
    private User profileUser(HttpServletRequest request) {
        String token = request.getHeader(CodeMap.X_ACCESS_TOKEN);
        if (StrUtil.isBlank(token)) return null;
        String realToken = redisService.getJwtToken(token);
        if (realToken == null) return null;
        User principal = JwtUtils.verifyAndGetUser(realToken);
        if (principal == null) return null;
        User current = userService.getById(principal.getId());
        return current != null && CodeMap.USER_STATUS_OK.equals(current.getStatus()) ? current : null;
    }

    @GetMapping("profile")
    public AjaxResult profile(HttpServletRequest request) {
        User current = profileUser(request);
        if (current == null) return AjaxResult.error(ErrorCode.AUTH_FAILED);
        current.setPmd(null);
        return AjaxResult.success(current);
    }

    // 仅允许更新本人资料；角色、账号状态和密码不能从此入口修改。
    @PostMapping("profile")
    public AjaxResult saveProfile(HttpServletRequest request,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String nickname,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String phone,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String part,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String remark) {
        User current = profileUser(request);
        if (current == null) return AjaxResult.error(ErrorCode.AUTH_FAILED);
        nickname = nickname.trim(); phone = phone.trim(); part = part.trim(); remark = remark.trim();
        if (nickname.isEmpty() || nickname.length() > 30 || phone.length() > 20
                || part.length() > 30 || remark.length() > 100) return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        boolean saved = userService.update(com.baomidou.mybatisplus.core.toolkit.Wrappers.<User>lambdaUpdate()
                .eq(User::getId, current.getId()).set(User::getNickname, nickname)
                .set(User::getPhone, phone).set(User::getPart, part).set(User::getRemark, remark));
        if (!saved) return AjaxResult.error("资料保存失败");
        return profile(request);
    }

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
