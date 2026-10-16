package com.xgls.web.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.CodeMap;
import com.xgls.web.base.ErrorCode;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.runner.TrainRunnerService;
import com.xgls.web.service.TrainResultArtifactService;
import com.xgls.web.service.TrainResultService;
import com.xgls.web.service.TrainTaskService;
import com.xgls.web.service.TrainingResultImageService;
import com.xgls.web.utils.SessionUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/trainMetrics")
public class TrainingMetricsController {
    private final TrainTaskService tasks;
    private final TrainResultService results;
    private final TrainResultArtifactService artifacts;
    private final TrainRunnerService runner;
    private final TrainingResultImageService images;

    public TrainingMetricsController(TrainTaskService tasks, TrainResultService results,
                                     TrainResultArtifactService artifacts, TrainRunnerService runner,
                                     TrainingResultImageService images) {
        this.tasks = tasks;
        this.results = results;
        this.artifacts = artifacts;
        this.runner = runner;
        this.images = images;
    }

    @PostMapping("task")
    public AjaxResult runningTask(@RequestParam Integer id) {
        TrainTask task = tasks.getById(id);
        if (task == null) return AjaxResult.error("训练任务不存在");
        if (!SessionUtil.hasAdminOrSelf(task.getUsername())) return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        if (task.getStarted_date() == null) return AjaxResult.error("该任务尚未开始训练");
        JSONObject options = tasks.runnerOptionsForTask(id);
        String engine = StrUtil.blankToDefault(options.getStr("engine"), "mmdet");
        try {
            JSONObject metrics = runner.getTrainingMetrics(task.getName(), engine, options,
                    null, task.getStarted_date());
            metrics.set("running", CodeMap.TRAIN_TASK_STATUS_RUN.equals(task.getStatus()));
            return AjaxResult.success(metrics);
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("result")
    public AjaxResult finishedResult(@RequestParam Integer id) {
        TrainResult result = results.getById(id);
        if (result == null) return AjaxResult.error("训练结果不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        JSONObject options = result.getTaskId() == null ? new JSONObject() : tasks.runnerOptionsForTask(result.getTaskId());
        Map<String, Object> detail = artifacts.detail(result);
        String engine = StrUtil.blankToDefault((String) detail.get("engine"),
                StrUtil.blankToDefault(options.getStr("engine"), "mmdet"));
        String resultDir = artifacts.resultDirectory(id);
        if (StrUtil.isBlank(resultDir)) return AjaxResult.error("该历史结果未保存精确产物目录，无法定位本次训练指标");
        options.set("runner_work_root", Path.of(resultDir).getParent().toString());
        try {
            return AjaxResult.success(runner.getTrainingMetrics(result.getTaskName(), engine, options,
                    resultDir, null));
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("result/images")
    public AjaxResult resultImages(@RequestParam Integer id) {
        TrainResult result = results.getById(id);
        if (result == null) return AjaxResult.error("训练结果不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        String stored = artifacts.resultDirectory(id);
        if (StrUtil.isBlank(stored)) return AjaxResult.error("该结果没有可读取的训练图片目录");
        try {
            var list = images.list(Path.of(stored));
            return AjaxResult.success(Map.of("images", list, "total", list.size()));
        } catch (IOException | IllegalArgumentException e) {
            return AjaxResult.error("读取训练图片失败：" + e.getMessage());
        }
    }

    @GetMapping("result/image")
    public void resultImage(@RequestParam Integer id, @RequestParam String name,
                            HttpServletResponse response) throws IOException {
        TrainResult result = results.getById(id);
        if (result == null) { response.sendError(404, "训练结果不存在"); return; }
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) { response.sendError(403); return; }
        String stored = artifacts.resultDirectory(id);
        if (StrUtil.isBlank(stored)) { response.sendError(404, "训练图片目录不存在"); return; }
        Path image;
        try {
            image = images.resolve(Path.of(stored), name);
        } catch (IllegalArgumentException e) {
            response.sendError(404, e.getMessage());
            return;
        }
        String lower = image.getFileName().toString().toLowerCase();
        response.setContentType(lower.endsWith(".png") ? "image/png" : "image/jpeg");
        response.setContentLengthLong(Files.size(image));
        response.setHeader("Cache-Control", "private, max-age=3600");
        Files.copy(image, response.getOutputStream());
    }
}
