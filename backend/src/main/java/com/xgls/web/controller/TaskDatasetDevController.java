package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.service.TaskDatasetDevService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/taskDatasetDev")
public class TaskDatasetDevController {

    private final TaskDatasetDevService taskDatasetDevService;

    @GetMapping("/tasks")
    public AjaxResult listTasks() {
        return taskDatasetDevService.listTasks();
    }

    @PostMapping("/tasks/list")
    public AjaxResult listTasksPost() {
        return taskDatasetDevService.listTasks();
    }

    @PostMapping("/tasks")
    public AjaxResult createTask(@RequestBody Map<String, Object> req) {
        return taskDatasetDevService.createTask(req);
    }

    @PostMapping("/tasks/delete")
    public AjaxResult deleteTask(@RequestBody Map<String, Object> req) {
        return taskDatasetDevService.deleteTask(req);
    }

    @PostMapping("/tasks/update")
    public AjaxResult updateTask(@RequestBody Map<String, Object> req) {
        return taskDatasetDevService.updateTask(req);
    }

    @PostMapping("/tasks/mapping")
    public AjaxResult updateTaskMapping(@RequestBody Map<String, Object> req) {
        return taskDatasetDevService.updateMappingRules(req);
    }

    @PostMapping("/tasks/export")
    public AjaxResult exportTask(@RequestBody Map<String, Object> req) {
        return taskDatasetDevService.exportTask(req);
    }

    @PostMapping("/tasks/clear")
    public AjaxResult clearTask(@RequestBody Map<String, Object> req) {
        return taskDatasetDevService.clearTask(req);
    }

    @PostMapping("/tasks/open-path")
    public AjaxResult openTaskPath(@RequestBody Map<String, Object> req) {
        return taskDatasetDevService.openTaskPath(req);
    }

    @GetMapping("/tasks/preview")
    public AjaxResult previewTask(
            @RequestParam("name") String name,
            @RequestParam(value = "perLabel", defaultValue = "3") Integer perLabel,
            jakarta.servlet.http.HttpServletRequest request) {
        return taskDatasetDevService.previewTask(name, perLabel == null ? 3 : perLabel, buildBaseUrl(request));
    }

    private String buildBaseUrl(jakarta.servlet.http.HttpServletRequest req) {
        String scheme = firstNonBlank(req.getHeader("X-Forwarded-Proto"), req.getScheme());
        String host = firstNonBlank(req.getHeader("X-Forwarded-Host"), req.getHeader("Host"));
        String portH = req.getHeader("X-Forwarded-Port");
        String prefix = java.util.Optional.ofNullable(req.getHeader("X-Forwarded-Prefix")).orElse("");
        if (host == null || host.isBlank()) {
            host = req.getServerName();
            int port = req.getServerPort();
            if (!("http".equalsIgnoreCase(scheme) && port == 80) && !("https".equalsIgnoreCase(scheme) && port == 443)) {
                host = host + ":" + port;
            }
        } else if (!host.contains(":")) {
            if (portH != null && !portH.isBlank()) {
                if (!("http".equalsIgnoreCase(scheme) && "80".equals(portH)) && !("https".equalsIgnoreCase(scheme) && "443".equals(portH))) {
                    host = host + ":" + portH;
                }
            }
        }
        if (!prefix.isEmpty() && prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        return scheme + "://" + host + prefix;
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    @GetMapping("/tasks/preview/objects")
    public AjaxResult previewObjects(
            @RequestParam("name") String name,
            @RequestParam("file") String file) {
        return taskDatasetDevService.previewObjects(name, file);
    }

    @GetMapping("/tasks/preview/image")
    public void previewImage(
            @RequestParam("name") String name,
            @RequestParam("file") String file,
            HttpServletResponse response) throws IOException {
        Path image = taskDatasetDevService.resolvePreviewImage(name, file);
        if (image == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String contentType = Files.probeContentType(image);
        response.setContentType(contentType == null ? "image/jpeg" : contentType);
        response.setHeader("Cache-Control", "no-store");
        Files.copy(image, response.getOutputStream());
    }
}
