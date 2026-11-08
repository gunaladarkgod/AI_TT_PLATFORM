package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.service.AlgorithmComponentService;
import com.xgls.web.service.RedisService;
import com.xgls.web.service.UserService;
import com.xgls.web.utils.AuthenticatedUserUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** APIs for independently versioned C algorithm repositories, not legacy sparse components. */
@RestController
@RequestMapping("/api/algorithm-components")
public class AlgorithmComponentController {
    private final AlgorithmComponentService components;
    private final RedisService redis;
    private final UserService users;

    public AlgorithmComponentController(AlgorithmComponentService components, RedisService redis, UserService users) {
        this.components = components;
        this.redis = redis;
        this.users = users;
    }

    @GetMapping
    public AjaxResult list(@RequestParam(required = false) String direction, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        return AjaxResult.success(components.list(direction));
    }

    @GetMapping("/training-profile")
    public AjaxResult trainingProfile(@RequestParam String id, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(components.trainingProfile(id)); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/install")
    public AjaxResult install(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!AuthenticatedUserUtil.isPlatformAdmin(AuthenticatedUserUtil.current(request, redis, users))) {
            return AjaxResult.error("下载算法源码会修改本机组件目录，请由平台管理员执行");
        }
        try { return AjaxResult.success(components.startInstall(body.get("id"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/remove")
    public AjaxResult remove(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!AuthenticatedUserUtil.isPlatformAdmin(AuthenticatedUserUtil.current(request, redis, users))) {
            return AjaxResult.error("移除本机算法源码会修改本机组件目录，请由平台管理员执行");
        }
        try { return AjaxResult.success(components.startRemove(body.get("id"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
}
