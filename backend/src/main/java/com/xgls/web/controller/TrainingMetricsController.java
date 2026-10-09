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
import com.xgls.web.utils.SessionUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/trainMetrics")
public class TrainingMetricsController {
    private final TrainTaskService tasks;
    private final TrainResultService results;
    private final TrainResultArtifactService artifacts;
    private final TrainRunnerService runner;

    public TrainingMetricsController(TrainTaskService tasks, TrainResultService results,
                                     TrainResultArtifactService artifacts, TrainRunnerService runner) {
        this.tasks = tasks;
        this.results = results;
        this.artifacts = artifacts;
        this.runner = runner;
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
}
