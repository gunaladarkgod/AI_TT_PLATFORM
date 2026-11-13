package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.User;
import com.xgls.web.service.*;
import com.xgls.web.utils.AuthenticatedUserUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class AlgorithmComponentController {
    private final AlgorithmComponentService service;
    private final RedisService redis;
    private final UserService users;
    public AlgorithmComponentController(AlgorithmComponentService service, RedisService redis, UserService users) { this.service = service; this.redis = redis; this.users = users; }
    @GetMapping("/api/{kind}-components")
    public AjaxResult list(@PathVariable String kind, @RequestParam(required = false) String direction, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(service.list(kind, direction)); } catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
    @PostMapping("/api/{kind}-components/install")
    public AjaxResult install(@PathVariable String kind, @RequestBody Map<String, String> body, HttpServletRequest request) { return mutate(kind, body, request, true); }
    @PostMapping("/api/{kind}-components/remove")
    public AjaxResult remove(@PathVariable String kind, @RequestBody Map<String, String> body, HttpServletRequest request) { return mutate(kind, body, request, false); }
    private AjaxResult mutate(String kind, Map<String, String> body, HttpServletRequest request, boolean install) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.isPlatformAdmin(user)) return AjaxResult.error("组件下载和移除需要平台管理员权限");
        try { return AjaxResult.success(install ? service.install(kind, body.get("id")) : service.remove(kind, body.get("id"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
}
