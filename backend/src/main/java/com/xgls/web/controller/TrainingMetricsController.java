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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Read several finished runs in one request for the cross experiment view.
     * The runner remains the source of truth for curves, while task options provide
     * the comparable experiment context (dataset, baseline and improvements).
     */
    @PostMapping("compare")
    public AjaxResult compare(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        List<Integer> ids = parseIds(body == null ? null : body.get("ids"));
        if (ids.size() < 2) return AjaxResult.error("至少选择两个已完成的训练结果");
        if (ids.size() > 8) return AjaxResult.error("一次最多对比 8 个训练结果");

        List<TrainResult> selected = results.listByIds(ids);
        Map<Integer, TrainResult> byId = new LinkedHashMap<>();
        for (TrainResult result : selected) {
            if (!SessionUtil.hasAdminOrSelf(result.getUserName())) {
                return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
            }
            artifacts.mergeMetadata(result);
            byId.put(result.getId(), result);
        }

        List<Map<String, Object>> experiments = new ArrayList<>();
        for (Integer id : ids) {
            TrainResult result = byId.get(id);
            if (result == null) return AjaxResult.error("训练结果 #" + id + " 不存在");
            experiments.add(buildComparisonEntry(result));
        }
        return AjaxResult.success(Map.of("experiments", experiments));
    }

    private Map<String, Object> buildComparisonEntry(TrainResult result) {
        JSONObject options = result.getTaskId() == null
                ? new JSONObject() : tasks.runnerOptionsForTask(result.getTaskId());
        Map<String, Object> detail = artifacts.detail(result);
        String detailEngine = detail.get("engine") == null ? null : String.valueOf(detail.get("engine"));
        String engine = StrUtil.blankToDefault(detailEngine,
                StrUtil.blankToDefault(options.getStr("engine"), result.getModelType()));
        if ("Ultralytics".equalsIgnoreCase(engine) || "yolo".equalsIgnoreCase(engine)) engine = "ultralytics";
        if (StrUtil.isBlank(engine)) engine = "mmdet";
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", result.getId());
        entry.put("name", StrUtil.blankToDefault(result.getResultName(), result.getTaskName()));
        entry.put("taskName", result.getTaskName());
        entry.put("engine", engine);
        String dataset = StrUtil.blankToDefault(result.getDataset(), options.getStr("ultralytics_data"));
        if ("ultralytics".equals(engine) && "coco_small".equals(dataset)
                && StrUtil.isNotBlank(options.getStr("ultralytics_data"))) {
            dataset = options.getStr("ultralytics_data");
        }
        entry.put("dataset", dataset);
        entry.put("network", StrUtil.blankToDefault(result.getNetworkName(), options.getStr("ultralytics_model")));
        entry.put("time", result.getTime());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("map", result.getMap());
        summary.put("ap50", result.getAp50());
        summary.put("ap75", result.getAp75());
        summary.put("aps", result.getAps());
        summary.put("apm", result.getApm());
        summary.put("apl", result.getApl());
        summary.put("durationSeconds", result.getDurationSeconds());
        entry.put("summary", summary);
        entry.put("context", comparisonContext(options, result));

        String resultDir = artifacts.resultDirectory(result.getId());
        if (StrUtil.isBlank(resultDir)) {
            entry.put("metrics", Map.of("series", List.of(), "message", "结果产物目录不存在"));
            entry.put("peakMemory", null);
            entry.put("classAp", Map.of());
            return entry;
        }
        options.set("runner_work_root", Path.of(resultDir).getParent().toString());
        try {
            JSONObject metrics = runner.getTrainingMetrics(result.getTaskName(), engine, options, resultDir, null);
            entry.put("metrics", metrics);
            entry.put("peakMemory", peakMemory(metrics));
            entry.put("classAp", classAp(metrics));
        } catch (IllegalStateException error) {
            entry.put("metrics", Map.of("series", List.of(), "message", error.getMessage()));
            entry.put("peakMemory", null);
            entry.put("classAp", Map.of());
        }
        return entry;
    }

    private Map<String, Object> comparisonContext(JSONObject options, TrainResult result) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("direction", options.getStr("research_direction"));
        context.put("baseline", options.getStr("research_baseline"));
        context.put("improvements", options.get("ultralytics_improvement_ids"));
        Map<String, Object> params = new LinkedHashMap<>();
        Object raw = options.get("ultralytics_parameters");
        if (raw instanceof JSONObject object) {
            for (String key : object.keySet()) params.put(key, object.get(key));
        } else if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> item : map.entrySet()) params.put(String.valueOf(item.getKey()), item.getValue());
        }
        if (StrUtil.isNotBlank(options.getStr("ultralytics_model"))) {
            params.putIfAbsent("model", options.getStr("ultralytics_model"));
        }
        context.put("parameters", params);
        context.put("configPath", result.getConfigPath());
        return context;
    }

    private Double peakMemory(JSONObject metrics) {
        double peak = Double.NEGATIVE_INFINITY;
        Object raw = metrics.get("series");
        if (!(raw instanceof Collection<?> series)) return null;
        for (Object item : series) {
            if (!(item instanceof JSONObject line)) continue;
            String label = StrUtil.blankToDefault(line.getStr("label"), line.getStr("key"));
            if (!label.toLowerCase().contains("memory") && !label.contains("显存")) continue;
            Object pointsRaw = line.get("points");
            if (!(pointsRaw instanceof Collection<?> points)) continue;
            for (Object point : points) {
                if (!(point instanceof JSONObject value)) continue;
                Number number = value.get("value", Number.class);
                if (number != null && Double.isFinite(number.doubleValue())) peak = Math.max(peak, number.doubleValue());
            }
        }
        return peak == Double.NEGATIVE_INFINITY ? null : peak;
    }

    private Map<String, Double> classAp(JSONObject metrics) {
        Map<String, Double> values = new LinkedHashMap<>();
        Object raw = metrics.get("series");
        if (!(raw instanceof Collection<?> series)) return values;
        for (Object item : series) {
            if (!(item instanceof JSONObject line) || !"accuracy".equals(line.getStr("group"))) continue;
            String label = StrUtil.blankToDefault(line.getStr("label"), line.getStr("key"));
            String lower = label.toLowerCase();
            if (lower.contains("map") || lower.contains("ap50") || lower.contains("ap75")) continue;
            Object pointsRaw = line.get("points");
            if (!(pointsRaw instanceof Collection<?> points) || points.isEmpty()) continue;
            Object last = new ArrayList<>(points).get(points.size() - 1);
            if (last instanceof JSONObject point) {
                Number value = point.get("value", Number.class);
                if (value != null) values.put(label, value.doubleValue());
            }
        }
        return values;
    }

    private List<Integer> parseIds(Object raw) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (raw instanceof Collection<?> values) {
            for (Object value : values) {
                try {
                    int id = Integer.parseInt(String.valueOf(value));
                    if (id > 0) ids.add(id);
                } catch (NumberFormatException ignored) { }
            }
        }
        return new ArrayList<>(ids);
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
