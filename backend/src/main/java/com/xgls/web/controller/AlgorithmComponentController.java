package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.User;
import com.xgls.web.service.*;
import com.xgls.web.utils.AuthenticatedUserUtil;
import com.xgls.web.utils.SystemDirectoryOpener;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.io.IOException;

@RestController
public class AlgorithmComponentController {
    private final AlgorithmComponentService service;
    private final CustomAlgorithmService custom;
    private final RedisService redis;
    private final UserService users;
    @Autowired private ComponentTrainingService training;

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
    @PostMapping("/api/{kind}-components/open-path")
    public AjaxResult openPath(@PathVariable String kind, @RequestBody Map<String, String> body, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(Map.of("path", SystemDirectoryOpener.openDirectory(service.localPath(kind, body.get("id"))).toString())); }
        catch (IllegalArgumentException | IOException e) { return AjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/api/component-training/configuration")
    public AjaxResult componentTrainingConfiguration(@RequestParam String kind, @RequestParam String id, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(training.inspectConfiguration(kind, id)); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/api/component-training/configuration")
    public AjaxResult saveComponentTrainingConfiguration(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("训练配置需要组件维护权限");
        try { return AjaxResult.success(training.saveConfiguration(body.get("kind"), body.get("id"), body.get("model"), body.get("pretrained"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @GetMapping("/api/algorithm-components/training-profile")
    public AjaxResult trainingProfile(@RequestParam String id, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(service.profile("algorithm", id)); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

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
        try { return AjaxResult.success(custom.upload(user.getId(), direction, baselineId, name, file)); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/api/custom-algorithms/upload/complete")
    public AjaxResult completeCustomUpload(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("算法上传需要组件维护权限");
        try { return AjaxResult.success(custom.complete(user.getId(), body.get("token"), body.get("model"), body.get("weight"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/api/custom-algorithms/upload/cancel")
    public AjaxResult cancelCustomUpload(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("算法上传需要组件维护权限");
        custom.cancel(user.getId(), body.get("token"));
        return AjaxResult.success();
    }

    @GetMapping("/api/custom-algorithms/configuration")
    public AjaxResult inspectCustomAlgorithm(@RequestParam String id, HttpServletRequest request) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("算法配置需要组件维护权限");
        try { return AjaxResult.success(custom.inspectExisting(id)); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/api/custom-algorithms/configuration")
    public AjaxResult configureCustomAlgorithm(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("算法配置需要组件维护权限");
        try { return AjaxResult.success(custom.configureExisting(body.get("id"), body.get("model"), body.get("weight"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/api/custom-algorithms/remove")
    public AjaxResult removeCustomAlgorithm(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("算法移除需要平台、基线或改进维护权限");
        try { custom.remove(body.get("id")); return AjaxResult.success(); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("/api/custom-algorithms/open-path")
    public AjaxResult openCustomAlgorithmPath(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (AuthenticatedUserUtil.current(request, redis, users) == null) return AjaxResult.error("请先登录");
        try { return AjaxResult.success(Map.of("path", SystemDirectoryOpener.openDirectory(custom.localPath(body.get("id"))).toString())); }
        catch (IllegalArgumentException | IOException e) { return AjaxResult.error(e.getMessage()); }
    }

    private AjaxResult mutate(String kind, Map<String, String> body, HttpServletRequest request, boolean install) {
        User user = AuthenticatedUserUtil.current(request, redis, users);
        if (!AuthenticatedUserUtil.canManageResearchComponents(user)) return AjaxResult.error("组件下载和移除需要平台、基线或改进维护权限");
        try { return AjaxResult.success(install ? service.install(kind, body.get("id")) : service.remove(kind, body.get("id"))); }
        catch (IllegalArgumentException e) { return AjaxResult.error(e.getMessage()); }
    }
}
