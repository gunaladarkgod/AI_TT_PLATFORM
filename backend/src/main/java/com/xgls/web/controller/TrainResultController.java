package com.xgls.web.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xgls.web.base.CodeMap;
import com.xgls.web.vo.query.TrainTaskQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.service.TrainResultService;
import com.xgls.web.service.TrainTaskService;
import com.xgls.web.service.TrainResultArtifactService;
import com.xgls.web.runner.TrainRunnerService;
import com.xgls.web.utils.SessionUtil;
import com.xgls.web.utils.SystemDirectoryOpener;
import com.xgls.web.base.ErrorCode;
import cn.hutool.core.util.StrUtil;
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
    private TrainResultArtifactService trainResultArtifactService;

    @Autowired
    private com.xgls.web.service.LocalTrainResultService localResults;

    private TrainResult findResult(Integer id) {
        TrainResult local = localResults.get(id);
        return local != null ? local : (id > 0 ? trainResultService.getById(id) : null);
    }

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
        List<TrainResult> legacy;
        try { legacy = trainResultService.list(wrapper); }
        catch (Exception ignored) { legacy = List.of(); }
        List<TrainTask> tasks;
        try { tasks = trainTaskService.list(); }
        catch (Exception ignored) { tasks = List.of(); }
        List<TrainResult> records = new ArrayList<>(localResults.list(tasks, legacy));

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

    @PostMapping("detail")
    public AjaxResult resultDetail(Integer id) {
        if (id == null) return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        TrainResult result = findResult(id);
        if (result == null) return AjaxResult.error("结果记录不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
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
        TrainResult result = findResult(id);
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

    @PostMapping("log")
    public AjaxResult resultLog(Integer id) {
        if (id == null) return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        TrainResult result = findResult(id);
        if (result == null) return AjaxResult.error("结果记录不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        try { return AjaxResult.success(localResults.log(id)); }
        catch (IllegalStateException e) { return AjaxResult.error(e.getMessage()); }
    }

    @PostMapping("config/read")
    public AjaxResult readResultConfig(Integer id, Boolean includeText) {
        if (id == null) return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        TrainResult result = findResult(id);
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
        TrainResult result = findResult(id);
        if (result == null) return AjaxResult.error("结果记录不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }
        try {
            JSONObject opened = trainRunnerService.openResultDirectory(
                    result.getTaskName(), result.getTime(), runnerOptionsForResult(result),
                    trainResultArtifactService.resultDirectory(result.getId()));
            Path openedPath = SystemDirectoryOpener.openDirectory(Path.of(opened.getStr("work_dir")));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", result.getId());
            data.put("path", openedPath.toString());
            return AjaxResult.success(data);
        } catch (IllegalStateException | IOException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 上传一张本地图片，使用该条完成训练的权重和配置进行推理。 */
    @PostMapping(value = "infer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult inferResult(@RequestParam Integer id, @RequestParam("file") MultipartFile file) {
        if (id == null || file == null || file.isEmpty()) return AjaxResult.error("请选择一张推理图片");
        TrainResult result = findResult(id);
        if (result == null) return AjaxResult.error("结果记录不存在");
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        if (file.getSize() > 10L * 1024 * 1024) return AjaxResult.error("推理图片不能超过 10 MB");
        try {
            byte[] bytes = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                return AjaxResult.error("请选择 JPG、PNG、BMP 或 GIF 格式的有效图片");
            }
            JSONObject options = runnerOptionsForResult(result);
            TrainTask task = result.getTaskId() == null ? null : trainTaskService.getById(result.getTaskId());
            JSONObject payload = new JSONObject();
            payload.set("engine", trainResultArtifactService.readMeta(id).getStr("engine", resolveInferenceEngine(options, task)));
            payload.set("result_dir", trainResultArtifactService.resultDirectory(id));
            payload.set("training_python_path", options == null ? null : options.getStr("training_python_path"));
            payload.set("image_name", file.getOriginalFilename());
            payload.set("image_base64", Base64.getEncoder().encodeToString(bytes));
            return AjaxResult.success(trainRunnerService.inferResult(
                    result.getTaskName(), result.getTime(), options, payload));
        } catch (IOException e) {
            return AjaxResult.error("读取推理图片失败：" + e.getMessage());
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("del")
    public AjaxResult delete(Integer id) {
        if (id == null) {
            return AjaxResult.error(ErrorCode.PARAMS_WRONG);
        }
        TrainResult result = findResult(id);
        if (result == null) {
            return AjaxResult.success("结果记录不存在");
        }
        if (!SessionUtil.hasAdminOrSelf(result.getUserName())) {
            return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
        }

        if (Boolean.TRUE.equals(result.getTraining())) return AjaxResult.error("训练中结果不能删除");
        final boolean fileDeleted;
        try {
            fileDeleted = trainRunnerService.deleteResultFiles(
                    result.getTaskName(), result.getTime(), runnerOptionsForResult(result),
                    trainResultArtifactService.resultDirectory(result.getId()));
        } catch (IllegalStateException e) {
            return AjaxResult.error(e.getMessage());
        }
        if (id > 0 && !trainResultService.removeById(id)) {
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
            if (id != null && id != 0) ids.add(id);
        }
        if (ids.isEmpty()) return AjaxResult.error(ErrorCode.PARAMS_WRONG);

        List<TrainResult> results = ids.stream().map(this::findResult).filter(java.util.Objects::nonNull).toList();
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
            if (Boolean.TRUE.equals(result.getTraining())) {
                errors.add("训练中结果不能删除：" + result.getTaskName());
                continue;
            }
            try {
                boolean fileDeleted = trainRunnerService.deleteResultFiles(
                        result.getTaskName(), result.getTime(), runnerOptionsForResult(result),
                        trainResultArtifactService.resultDirectory(result.getId()));
                if (id > 0 && !trainResultService.removeById(id)) {
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
        JSONObject options = result.getTaskId() == null ? new JSONObject() : trainTaskService.runnerOptionsForTask(result.getTaskId());
        if (options == null) options = new JSONObject();
        String dir = trainResultArtifactService.resultDirectory(result.getId());
        if (dir == null && StrUtil.isNotBlank(trainResultArtifactService.readMeta(result.getId()).getStr("resultDir"))) {
            throw new IllegalStateException("该结果的本地目录不存在，请刷新结果列表");
        }
        if (dir != null) options.set("runner_work_root", Path.of(dir).getParent().toString());
        return options;
    }

    private String resolveInferenceEngine(JSONObject options, TrainTask task) {
        String explicit = options == null ? null : StrUtil.trim(options.getStr("engine"));
        if (StrUtil.isNotBlank(explicit)) return explicit.toLowerCase();
        String type = task == null ? null : StrUtil.trim(task.getType());
        if ("ultralytics".equalsIgnoreCase(type) || "yolo".equalsIgnoreCase(type)) return "ultralytics";
        if ("custom".equalsIgnoreCase(type) || "自定义".equals(type)
                || (options != null && "fixed".equalsIgnoreCase(options.getStr("runner_mode")))) return "custom";
        return "mmdet";
    }
}
