package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.User;
import com.xgls.web.service.*;
import com.xgls.web.utils.AuthenticatedUserUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
public class AlgorithmComponentController {
    private final AlgorithmComponentService service;
    private final CustomAlgorithmService custom;
    private final RedisService redis;
    private final UserService users;

    @Autowired
    public AlgorithmComponentController(AlgorithmComponentService service, CustomAlgorithmService custom, RedisService redis, UserService users) {
        this.service = service; this.custom = custom; this.redis = redis; this.users = users;
    }

    /** Keeps direct construction used by older integrations source-compatible. */
    public AlgorithmComponentController(AlgorithmComponentService service, RedisService redis, UserService users) {
        this(service, new CustomAlgorithmService(), redis, users);
    }
    @GetMapping("/api/{kind}-components")
    public AjaxResult list(@PathVariable String kind, @RequestParam(required = false) String direction, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(service.list(kind, direction)); } catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
    @PostMapping("/api/{kind}-components/install")
    public AjaxResult install(@PathVariable String kind, @RequestBody Map<String, String> body, HttpServletRequest request) { return mutate(kind, body, request, true); }
    @PostMapping("/api/{kind}-components/remove")
    public AjaxResult remove(@PathVariable String kind, @RequestBody Map<String, String> body, HttpServletRequest request) { return mutate(kind, body, request, false); }

    @GetMapping("/api/custom-algorithms")
    public AjaxResult customAlgorithms(@RequestParam(required = false) String direction, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(custom.list(direction)); } catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping(value = "/api/custom-algorithms/upload", consumes = "multipart/form-data")
    public AjaxResult uploadCustomAlgorithm(@RequestParam String direction, @RequestParam(required = false, defaultValue = "") String baselineId,
                                            @RequestParam String name, @RequestPart("file") MultipartFile file,
                                            HttpServletRequest request) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("算法上传需要平台、基线或改进维护权限");
        try { return AjaxResult.success(custom.upload(direction, baselineId, name, file)); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/api/custom-algorithms/remove")
    public AjaxResult removeCustomAlgorithm(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("算法移除需要平台、基线或改进维护权限");
        try { custom.remove(body.get("id")); return AjaxResult.success(); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    private AjaxResult mutate(String kind, Map<String, String> body, HttpServletRequest request, boolean install) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("组件下载和移除需要平台、基线或改进维护权限");
        try { return AjaxResult.success(install ? service.install(kind, body.get("id")) : service.remove(kind, body.get("id"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
}
