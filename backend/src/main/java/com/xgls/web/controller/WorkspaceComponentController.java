package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.service.*;
import com.xgls.web.utils.AuthenticatedUserUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace/components")
public class WorkspaceComponentController {
    private final WorkspaceComponentService components;
    private final RedisService redis;
    private final UserService users;
    public WorkspaceComponentController(WorkspaceComponentService components, RedisService redis, UserService users) {
        this.components = components; this.redis = redis; this.users = users;
    }
    @GetMapping public AjaxResult list(@RequestParam(defaultValue = "ultralytics") String engine, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(components.list(engine)); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
    @PostMapping("/download") public AjaxResult download(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!AuthenticatedUserUtil.isPlatformAdmin(AuthenticatedUserUtil.current(request, redis, users)))
            return AjaxResult.error("组件下载会修改平台工作区，请由平台管理员执行");
        try { return AjaxResult.success(components.start(body.get("id"), body.get("engine"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
}
