package com.xgls.web.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Path;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.CodeMap;
import com.xgls.web.vo.query.TrainTaskQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.entity.TrainExt;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.service.TrainResultService;
import com.xgls.web.service.TrainExtService;
import com.xgls.web.service.TrainTaskService;
import com.xgls.web.service.TrainResultArtifactService;
import com.xgls.web.runner.TrainRunnerService;
import com.xgls.web.utils.SessionUtil;
import com.xgls.web.utils.SystemDirectoryOpener;
import com.xgls.web.base.ErrorCode;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "训练结果管理")
@RestController
@RequestMapping("/trainResult")
public class TrainResultController {

    @Autowired
    private TrainResultService trainResultService;

    @Autowired
    private TrainRunnerService trainRunnerService;

    @Autowired
    private TrainTaskService trainTaskService;

    @Autowired
    private TrainExtService trainExtService;

    @Autowired
    private TrainResultArtifactService trainResultArtifactService;

    @PostMapping("all")
    public AjaxResult queryAll(TrainTaskQuery query) {
        LambdaQueryWrapper<TrainResult> wrapper = new LambdaQueryWrapper<>();
        /** 分页信息 */
        Long current = query.getCurrent();
        Long size = query.getSize();
        if (current == null) {
            current = CodeMap.PAGE_NO_DEFAULT;
        }
        if (size == null) {
            size = CodeMap.PAGE_SIZE_DEFAULT;
        }
        wrapper.orderByDesc(TrainResult::getTime);
        List<TrainResult> records = new ArrayList<>();

        // 正在训练的任务从日志实时读取指标，并固定放在正式结果之前。
        LambdaQueryWrapper<TrainTask> runningWrapper = new LambdaQueryWrapper<>();
        runningWrapper.eq(TrainTask::getStatus, CodeMap.TRAIN_TASK_STATUS_RUN)
                .orderByDesc(TrainTask::getStarted_date);
        for (TrainTask task : trainTaskService.list(runningWrapper)) {
            records.add(buildRunningResult(task));
        }
        List<TrainResult> finishedRecords = trainResultService.list(wrapper);
        for (TrainResult result : finishedRecords) {
            result.setModelType(resolveResultModelType(result.getTaskId(), null));
            trainResultArtifactService.mergeMetadata(result);
        }
        records.addAll(finishedRecords);

        long total = records.size();
        long safeCurrent = Math.max(1L, current);
        long safeSize = Math.max(1L, size);
        long from = (safeCurrent - 1L) * safeSize;
        List<TrainResult> pageRecords = new ArrayList<>();
        if (from < total) {
            int start = (int) from;
            int end = (int) Math.min(total, from + safeSize);
            pageRecords.addAll(records.subList(start, end));
        }
        Page<TrainResult> page = new Page<>(safeCurrent, safeSize, total);
        page.setRecords(pageRecords);
        return AjaxResult.success(page);
    }

    private TrainResult buildRunningResult(TrainTask task) {
        TrainResult result = new TrainResult();
        result.setTaskId(task.getId());
        result.setTaskName(task.getName());
        result.setUserName(task.getUsername());
        result.setModelType(resolveResultModelType(task.getId(), task.getType()));
        result.setDataset("coco_small");
        result.setTime(task.getStarted_date());
        result.setTraining(true);
        result.setResultName(task.getName());

        TrainExt ext = trainExtService.getById(task.getId());
        if (ext != null && StrUtil.isNotBlank(ext.getParams()) && JSONUtil.isTypeJSONObject(ext.getParams())) {
            result.setNetworkName(JSONUtil.parseObj(ext.getParams()).getStr("network_name"));
        }
        try {
            JSONObject log = trainRunnerService.getLatestTrainLog(task.getName(), 5000);
            String content = log.getStr("content", "");
            result.setMap(findLatestMetric(content, "bbox_mAP(?!_)"));
            result.setAp50(findLatestMetric(content, "bbox_mAP_50"));
            result.setAp75(findLatestMetric(content, "bbox_mAP_75"));
            result.setAps(findLatestMetric(content, "bbox_mAP_s"));
            result.setApm(findLatestMetric(content, "bbox_mAP_m"));
            result.setApl(findLatestMetric(content, "bbox_mAP_l"));
        } catch (Exception ignore) {
            // Runner 刚创建任务但尚未写出首轮日志时，先展示空指标的训练中记录。
        }
        return result;
    }

    private Double findLatestMetric(String content, String metricPattern) {
        Pattern pattern = Pattern.compile(
                "(?:^|\\s)(?:coco/)?" + metricPattern + "\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content == null ? "" : content);
        Double latest = null;
        while (matcher.find()) {
            try {
                latest = Math.max(0D, Double.parseDouble(matcher.group(1)));
            } catch (NumberFormatException ignore) {
            }
        }
        return latest;
    }

    /** 结果类别以任务实际 Runner 模式为准，兼容历史结果表中误写为 mmdet 的自定义任务。 */
    private String resolveResultModelType(Integer taskId, String taskType) {
        if (taskId != null) {
            JSONObject options = trainTaskService.runnerOptionsForTask(taskId);
            if ("fixed".equalsIgnoreCase(options.getStr("runner_mode"))) return "自定义";
        }
        if ("custom".equalsIgnoreCase(taskType) || "自定义".equals(taskType)) return "自定义";
        return "mmdet";
    }

    @PostMapping("detail")
    public AjaxResult resultDetail(Integer id) {
        if (id == null) return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        TrainResult result = trainResultService.getById(id);
        if (result == null) return AjaxResult.error("结果记录不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        result.setModelType(resolveResultModelType(result.getTaskId(), null));
        return AjaxResult.success(trainResultArtifactService.detail(result));
    }

    @PostMapping("meta/update")
    public AjaxResult updateResultMetadata(@RequestBody Map<String, Object> body) {
        Object rawId = body == null ? null : body.get("id");
        if (rawId == null) return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        Integer id;
        try {
            id = rawId instanceof Number ? ((Number) rawId).intValue() : Integer.valueOf(String.valueOf(rawId));
        } catch (NumberFormatException e) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        TrainResult result = trainResultService.getById(id);
        if (result == null) return AjaxResult.error("结果记录不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        Collection<?> tags = body.get("tags") instanceof Collection<?> values ? values : List.of();
        try {
            return AjaxResult.success(trainResultArtifactService.updateMetadata(result,
                    String.valueOf(body.getOrDefault("resultName", "")),
                    String.valueOf(body.getOrDefault("remark", "")), tags));
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("config/read")
    public AjaxResult readResultConfig(Integer id, Boolean includeText) {
        if (id == null) return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        TrainResult result = trainResultService.getById(id);
        if (result == null) return AjaxResult.error("结果记录不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        try {
            return AjaxResult.success(trainResultArtifactService.readConfigSnapshot(result, Boolean.TRUE.equals(includeText)));
        } catch (IOException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("open-path")
    public AjaxResult openResultPath(Integer id) {
        if (id == null) return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        TrainResult result = trainResultService.getById(id);
        if (result == null) return AjaxResult.error("结果记录不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }
        try {
            JSONObject opened = trainRunnerService.openResultDirectory(
                    result.getTaskName(), result.getTime(), runnerOptionsForResult(result));
            Path openedPath = SystemDirectoryOpener.openDirectory(Path.of(opened.getStr("work_dir")));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", result.getId());
            data.put("path", openedPath.toString());
            return AjaxResult.success(data);
        } catch (IllegalStateException | IOException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("del")
    public AjaxResult delete(Integer id) {
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        TrainResult result = trainResultService.getById(id);
        if (result == null) {
            return AjaxResult.success("结果记录不存在");
        }
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }

        final boolean fileDeleted;
        try {
            fileDeleted = trainRunnerService.deleteResultFiles(
                    result.getTaskName(), result.getTime(), runnerOptionsForResult(result));
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
        if (!trainResultService.removeById(id)) {
            return AjaxResult.error("本地文件已处理，但结果记录删除失败");
        }
        trainResultArtifactService.deleteMetadata(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("fileDeleted", fileDeleted);
        return AjaxResult.success(data);
    }

    /** 批量删除已完成的训练结果，并同步清理每条结果对应的 Runner 本地产物。 */
    @PostMapping("del/batch")
    public AjaxResult deleteBatch(@RequestBody Map<String, List<Integer>> request) {
        List<Integer> incomingIds = request == null ? null : request.get("ids");
        if (incomingIds == null || incomingIds.isEmpty()) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (Integer id : incomingIds) {
            if (id != null && id > 0) ids.add(id);
        }
        if (ids.isEmpty()) return AjaxResult.error(ErrorCode.PARAMS_WRONG);

        List<TrainResult> results = trainResultService.listByIds(ids);
        for (TrainResult result : results) {
            if (!SessionUtil.hasAdminOrSelf(result.getUserName())) {
                return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
            }
        }

        Map<Integer, TrainResult> resultMap = new LinkedHashMap<>();
        for (TrainResult result : results) resultMap.put(result.getId(), result);
        List<Integer> deletedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int fileDeletedCount = 0;
        for (Integer id : ids) {
            TrainResult result = resultMap.get(id);
            if (result == null) {
                errors.add("结果记录 #" + id + " 不存在");
                continue;
            }
            try {
                boolean fileDeleted = trainRunnerService.deleteResultFiles(
                        result.getTaskName(), result.getTime(), runnerOptionsForResult(result));
                if (!trainResultService.removeById(id)) {
                    errors.add("结果“" + result.getTaskName() + "”记录删除失败");
                    continue;
                }
                trainResultArtifactService.deleteMetadata(id);
                deletedIds.add(id);
                if (fileDeleted) fileDeletedCount++;
            } catch (IllegalStateException e) {
                errors.add("结果“" + result.getTaskName() + "”删除失败：" + e.getMessage());
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestedCount", ids.size());
        data.put("deletedCount", deletedIds.size());
        data.put("fileDeletedCount", fileDeletedCount);
        data.put("deletedIds", deletedIds);
        data.put("errors", errors);
        return AjaxResult.success(data);
    }

    private JSONObject runnerOptionsForResult(TrainResult result) {
        if (result == null || result.getTaskId() == null) return null;
        return trainTaskService.runnerOptionsForTask(result.getTaskId());
    }
}
