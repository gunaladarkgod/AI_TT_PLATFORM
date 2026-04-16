package com.xgls.web.controller;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.service.TaskDatasetDevService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public AjaxResult deleteTask(@RequestBody Map<String, String> req) {
        return taskDatasetDevService.deleteTask(req != null ? req.get("name") : null);
    }

    @PostMapping("/tasks/mapping")
    public AjaxResult updateTaskMapping(@RequestBody Map<String, Object> req) {
        return taskDatasetDevService.updateMappingRules(req);
    }
}
