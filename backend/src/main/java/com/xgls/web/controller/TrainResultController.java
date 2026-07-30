package com.xgls.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.CodeMap;
import com.xgls.web.vo.query.TrainTaskQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.xgls.web.runner.TrainRunnerService;
import com.xgls.web.utils.SessionUtil;
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
        records.addAll(trainResultService.list(wrapper));

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
        result.setModelType("mmdet");
        result.setDataset("coco_small");
        result.setTime(task.getStarted_date());
        result.setTraining(true);

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
            fileDeleted = trainRunnerService.deleteResultFiles(result.getTaskName(), result.getTime());
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
        if (!trainResultService.removeById(id)) {
            return AjaxResult.error("本地文件已处理，但结果记录删除失败");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("fileDeleted", fileDeleted);
        return AjaxResult.success(data);
    }
}
