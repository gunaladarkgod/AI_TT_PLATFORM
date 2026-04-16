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
}
