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

/** APIs for independently versioned B baseline repositories and their validation state. */
@RestController
@RequestMapping("/api/baseline-components")
public class BaselineComponentController {
    private final AlgorithmComponentService components;
    private final RedisService redis;
    private final UserService users;

    public BaselineComponentController(AlgorithmComponentService components, RedisService redis, UserService users) {
        this.components = components;
        this.redis = redis;
        this.users = users;
    }

    @GetMapping
    public AjaxResult list(@RequestParam(required = false) String direction, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        return AjaxResult.success(components.list("baseline", direction));
    }

    @GetMapping("/validation-profile")
    public AjaxResult validationProfile(@RequestParam String id, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(components.profile("baseline", id)); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/install")
    public AjaxResult install(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!AuthenticatedUserUtil.canManageResearchComponents(AuthenticatedUserUtil.current(request, redis, users))) {
            return AjaxResult.error("下载基线源码会修改本机组件目录，需要组件维护权限");
        }
        try { return AjaxResult.success(components.install("baseline", body.get("id"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/remove")
    public AjaxResult remove(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!AuthenticatedUserUtil.canManageResearchComponents(AuthenticatedUserUtil.current(request, redis, users))) {
            return AjaxResult.error("移除本机基线源码会修改本机组件目录，需要组件维护权限");
        }
        try { return AjaxResult.success(components.remove("baseline", body.get("id"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
}
