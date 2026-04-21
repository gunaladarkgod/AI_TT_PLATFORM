package com.xgls.web.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.InstanceDatasetMid;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.entity.User;
import com.xgls.web.utils.SessionUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TaskDatasetDevService {

    @Value("${sys.original-dataset-root:/home/omen1/AI_TT_Platform/data/original_dataset}")
    private String originalDatasetRoot;
    @Value("${sys.instancecfg.instancedata-mid-root:/home/omen1/AI_TT_Platform/data/instance_dataset_mid/}")
    private String instanceDatasetMidRoot;

    private final TaskDataset1Service taskDatasetService;
    private final OriginalDataset1Service originalDatasetService;
    private final InstanceDatasetMidService instanceDatasetMidService;
    private final JdbcTemplate jdbcTemplate;

    public TaskDatasetDevService(
            TaskDataset1Service taskDatasetService,
            OriginalDataset1Service originalDatasetService,
            InstanceDatasetMidService instanceDatasetMidService,
            JdbcTemplate jdbcTemplate) {
        this.taskDatasetService = taskDatasetService;
        this.originalDatasetService = originalDatasetService;
        this.instanceDatasetMidService = instanceDatasetMidService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public AjaxResult listTasks() {
        try {
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("读取任务定义失败: " + e.getMessage());
        }
    }

    public AjaxResult createTask(Map<String, Object> req) {
        String name = trim(req.get("name"));
        String desc = trim(req.get("desc"));
        List<String> targetSchema = toStringList(req.get("targetSchema"));
        List<String> testDatasets = toStringList(req.get("testDatasets"));

        if (StrUtil.isBlank(name)) return AjaxResult.error("请输入任务名称");
        if (targetSchema.isEmpty()) return AjaxResult.error("请至少填写一个目标类别");
        if (testDatasets.isEmpty()) return AjaxResult.error("请至少选择一个测试数据集");

        try {
            JSONObject root = readTaskRoot();
            if (root.containsKey(name)) {
                return AjaxResult.error("任务名称已存在，请更换后再创建");
            }

            JSONObject one = new JSONObject();
            one.set("desc", desc);
            one.set("target_schema", targetSchema);
            one.set("test_datasets", testDatasets);
            one.set("mapping_rules", new JSONObject());
            one.set("updated_time", LocalDateTime.now().toString());
            String username = currentUsername();
            if (StrUtil.isNotBlank(username)) {
                one.set("updated_by", username);
            }

            root.set(name, one);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("创建任务失败: " + e.getMessage());
        }
    }

    public AjaxResult deleteTask(Map<String, Object> req) {
        String taskName = trim(req != null ? req.get("name") : null);
        if (StrUtil.isBlank(taskName)) return AjaxResult.error("缺少任务名称");
        boolean alsoDeleteLocal = true;
        if (req != null && req.containsKey("alsoDeleteLocal")) {
            alsoDeleteLocal = Boolean.TRUE.equals(req.get("alsoDeleteLocal"));
        }
        try {
            JSONObject root = readTaskRoot();
            if (!root.containsKey(taskName)) {
                return AjaxResult.error("任务不存在");
            }
            if (alsoDeleteLocal) {
                clearExportedMidByTask(taskName);
            }
            root.remove(taskName);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("删除任务失败: " + e.getMessage());
        }
    }

    public AjaxResult updateTask(Map<String, Object> req) {
        String originalName = trim(req.get("originalName"));
        String name = trim(req.get("name"));
        String desc = trim(req.get("desc"));
        List<String> targetSchema = toStringList(req.get("targetSchema"));
        List<String> testDatasets = toStringList(req.get("testDatasets"));

        if (StrUtil.isBlank(originalName)) return AjaxResult.error("缺少原任务名称");
        if (StrUtil.isBlank(name)) return AjaxResult.error("请输入任务名称");
        if (targetSchema.isEmpty()) return AjaxResult.error("请至少填写一个目标类别");
        if (testDatasets.isEmpty()) return AjaxResult.error("请至少选择一个测试数据集");

        try {
            JSONObject root = readTaskRoot();
            if (!root.containsKey(originalName)) {
                return AjaxResult.error("任务不存在");
            }
            if (!StrUtil.equals(originalName, name) && root.containsKey(name)) {
                return AjaxResult.error("任务名称已存在，请更换后再保存");
            }

            JSONObject oldTask = root.getJSONObject(originalName);
            JSONObject one = new JSONObject();
            one.set("desc", desc);
            one.set("target_schema", targetSchema);
            one.set("test_datasets", testDatasets);
            one.set("mapping_rules", oldTask != null ? oldTask.getJSONObject("mapping_rules") : new JSONObject());
            if (oldTask != null) {
                carryExportMeta(oldTask, one);
            }
            one.set("updated_time", LocalDateTime.now().toString());
            String username = currentUsername();
            if (StrUtil.isNotBlank(username)) {
                one.set("updated_by", username);
            }

            root.remove(originalName);
            root.set(name, one);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("更新任务失败: " + e.getMessage());
        }
    }

    public AjaxResult exportTask(Map<String, Object> req) {
        String name = trim(req.get("name"));
        if (StrUtil.isBlank(name)) return AjaxResult.error("缺少任务名称");
        try {
            JSONObject root = readTaskRoot();
            JSONObject one = root.getJSONObject(name);
            if (one == null) return AjaxResult.error("任务不存在");

            MappingStatus mappingStatus = assessMappingStatus(one);
            if (!mappingStatus.ok) {
                return AjaxResult.error("导出前检查失败（映射不完整）：" + mappingStatus.detail);
            }

            List<String> datasetNames = jsonArrayToList(one.getJSONArray("test_datasets"));
            List<DatasetSource> sources = resolveDatasetSources(datasetNames);
            if (!sources.isEmpty()) {
                int exported = exportFromDatasetSources(name, one, sources);
                if (exported <= 0) {
                    return AjaxResult.error("导出失败：已定位到数据集路径，但未找到可复制的数据内容，请检查 images/annotations 目录结构。");
                }
            } else {
                TaskDataset taskDataset = findTaskDatasetForExport(name, one);
                if (taskDataset == null) {
                    return AjaxResult.error("未找到可匹配的任务数据集，且未解析到测试数据集路径。请确认外部导入记录存在，或先在原任务数据集页面完成“训测划分”。");
                }
                List<String> testPlan = buildDefaultTestPlan(taskDataset);
                List<String> trainOriginalIds = splitIds(taskDataset.getSupId());
                taskDatasetService.processTestPlan(taskDataset, testPlan, trainOriginalIds, 1);
            }

            one.set("last_export_time", LocalDateTime.now().toString());
            one.set("last_export_source_updated_time", one.getStr("updated_time", ""));
            one.set("last_export_by", currentUsername());
            one.set("last_export_mid_count", countMidByFatherName(name));
            root.set(name, one);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("导出失败: " + e.getMessage());
        }
    }

    public AjaxResult clearTask(Map<String, Object> req) {
        String name = trim(req != null ? req.get("name") : null);
        if (StrUtil.isBlank(name)) return AjaxResult.error("缺少任务名称");
        try {
            if (!hasExportedMidArtifacts(name)) {
                return AjaxResult.error("无需清除：当前没有已导出的中间数据集（数据库与目录均未发现对应内容）");
            }
            clearExportedMidByTask(name);
            JSONObject root = readTaskRoot();
            JSONObject one = root.getJSONObject(name);
            if (one != null) {
                // 移除键（比设空串更可靠，避免序列化/反序列化后仍被当成已导出）
                one.remove("last_export_time");
                one.remove("last_export_source_updated_time");
                one.remove("last_export_by");
                one.set("last_export_mid_count", 0);
                root.set(name, one);
                writeTaskRoot(root);
            }
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("清除失败: " + e.getMessage());
        }
    }

    public AjaxResult updateMappingRules(Map<String, Object> req) {
        String name = trim(req.get("name"));
        Object mappingRules = req.get("mappingRules");
        if (StrUtil.isBlank(name)) return AjaxResult.error("缺少任务名称");
        try {
            JSONObject root = readTaskRoot();
            if (!root.containsKey(name)) {
                return AjaxResult.error("任务不存在");
            }
            JSONObject one = root.getJSONObject(name);
            one.set("mapping_rules", normalizeMappingRules(mappingRules));
            one.set("updated_time", LocalDateTime.now().toString());
            String username = currentUsername();
            if (StrUtil.isNotBlank(username)) {
                one.set("updated_by", username);
            }
            root.set(name, one);
            writeTaskRoot(root);
            return AjaxResult.success(readTasksAsList());
        } catch (Exception e) {
            return AjaxResult.error("保存映射规则失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> readTasksAsList() throws Exception {
        JSONObject root = readTaskRoot();
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : root.keySet()) {
            JSONObject one = root.getJSONObject(key);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", key);
            row.put("desc", one.getStr("desc", ""));
            row.put("target_schema", jsonArrayToList(one.getJSONArray("target_schema")));
            row.put("test_datasets", jsonArrayToList(one.getJSONArray("test_datasets")));
            row.put("mapping_rules", one.getJSONObject("mapping_rules"));
            row.put("updated_time", one.getStr("updated_time", ""));
            row.put("updated_by", one.getStr("updated_by", ""));
            row.put("last_export_time", one.getStr("last_export_time", ""));
            row.put("last_export_by", one.getStr("last_export_by", ""));
            row.put("last_export_mid_count", one.getInt("last_export_mid_count", 0));
            String statusCode = resolveExportStatusCode(one);
            row.put("status_code", statusCode);
            row.put("status_text", statusText(statusCode));
            MappingStatus mappingStatus = assessMappingStatus(one);
            row.put("mapping_status_code", mappingStatus.code);
            row.put("mapping_status_text", mappingStatus.text);
            row.put("mapping_status_detail", mappingStatus.detail);
            out.add(row);
        }
        out.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        return out;
    }

    private JSONObject readTaskRoot() throws Exception {
        List<TaskDataset> rows = taskDatasetService.lambdaQuery()
                .orderByDesc(TaskDataset::getId)
                .list();
        LinkedHashMap<String, TaskDataset> latestByName = new LinkedHashMap<>();
        for (TaskDataset row : rows) {
            String name = trim(row.getName());
            if (StrUtil.isBlank(name)) {
                continue;
            }
            latestByName.putIfAbsent(name, row);
        }

        JSONObject root = new JSONObject(new LinkedHashMap<>());
        List<String> names = new ArrayList<>(latestByName.keySet());
        names.sort(String::compareToIgnoreCase);
        for (String name : names) {
            TaskDataset row = latestByName.get(name);
            JSONObject one = new JSONObject();
            one.set("desc", StrUtil.blankToDefault(row.getTaskDesc(), ""));
            one.set("target_schema", parseJsonArrayOrDefault(row.getTargetSchema(), deriveTargetSchema(row)));
            one.set("test_datasets", parseJsonArrayOrDefault(row.getTestDatasets(), deriveTestDatasets(row)));
            one.set("mapping_rules", parseJsonObjectOrDefault(row.getMappingRules(), new JSONObject()));

            LocalDateTime updatedTime = row.getDevUpdatedTime() != null ? row.getDevUpdatedTime() : row.getCreatedTime();
            one.set("updated_time", updatedTime != null ? updatedTime.toString() : "");
            one.set("updated_by", StrUtil.blankToDefault(row.getDevUpdatedBy(), StrUtil.blankToDefault(row.getUsername(), "")));

            one.set("last_export_time", row.getLastExportTime() != null ? row.getLastExportTime().toString() : "");
            one.set("last_export_source_updated_time",
                    row.getLastExportSourceUpdatedTime() != null ? row.getLastExportSourceUpdatedTime().toString() : "");
            one.set("last_export_by", StrUtil.blankToDefault(row.getLastExportBy(), ""));
            one.set("last_export_mid_count", row.getLastExportMidCount() == null ? 0 : row.getLastExportMidCount());
            root.set(name, one);
        }
        return root;
    }

    private void writeTaskRoot(JSONObject root) throws Exception {
        List<TaskDataset> rows = taskDatasetService.lambdaQuery()
                .orderByDesc(TaskDataset::getId)
                .list();
        LinkedHashMap<String, TaskDataset> latestByName = new LinkedHashMap<>();
        for (TaskDataset row : rows) {
            String name = trim(row.getName());
            if (StrUtil.isBlank(name)) {
                continue;
            }
            latestByName.putIfAbsent(name, row);
        }

        Set<String> targetNames = new LinkedHashSet<>(root.keySet());
        for (String existingName : latestByName.keySet()) {
            if (!targetNames.contains(existingName)) {
                taskDatasetService.remove(new LambdaQueryWrapper<TaskDataset>().eq(TaskDataset::getName, existingName));
            }
        }

        for (String name : root.keySet()) {
            JSONObject one = root.getJSONObject(name);
            if (one == null || StrUtil.isBlank(name)) {
                continue;
            }
            TaskDataset row = latestByName.get(name);
            boolean isNew = false;
            if (row == null) {
                row = new TaskDataset();
                row.setName(name);
                row.setSensorType("外部");
                row.setTargetType("复合");
                row.setDataFormat(0);
                row.setUsername(StrUtil.blankToDefault(currentUsername(), "admin"));
                row.setCreatedTime(LocalDateTime.now());
                row.setCoreId("");
                row.setCoreName("");
                row.setCoreTargetType("");
                row.setCoreImgNum(0);
                row.setCoreAnnoNum(0);
                row.setCoreClassNum(0);
                row.setCoreClassList("{}");
                row.setCoreDataPath("");
                row.setCoreAnnoPath("");
                row.setSupId("");
                row.setSupName("");
                row.setSupTargetType("");
                row.setSupImgNum(0);
                row.setSupAnnoNum(0);
                row.setSupClassNum(0);
                row.setSupClassList("{}");
                row.setSupDataPath("");
                row.setSupAnnoPath("");
                isNew = true;
            }

            row.setTaskDesc(one.getStr("desc", ""));
            row.setTargetSchema(JSONUtil.toJsonStr(parseJsonArrayOrDefault(one.get("target_schema"), new JSONArray())));
            row.setTestDatasets(JSONUtil.toJsonStr(parseJsonArrayOrDefault(one.get("test_datasets"), new JSONArray())));
            row.setMappingRules(JSONUtil.toJsonStr(parseJsonObjectOrDefault(one.get("mapping_rules"), new JSONObject())));
            row.setDevUpdatedTime(parseTime(one.getStr("updated_time", "")));
            row.setDevUpdatedBy(one.getStr("updated_by", ""));
            row.setLastExportTime(parseTime(one.getStr("last_export_time", "")));
            row.setLastExportSourceUpdatedTime(parseTime(one.getStr("last_export_source_updated_time", "")));
            row.setLastExportBy(one.getStr("last_export_by", ""));
            row.setLastExportMidCount(one.getInt("last_export_mid_count", 0));

            if (isNew) {
                taskDatasetService.save(row);
            } else {
                taskDatasetService.updateById(row);
            }
        }
    }

    private JSONArray deriveTargetSchema(TaskDataset row) {
        JSONArray arr = new JSONArray();
        String coreClassList = trim(row.getCoreClassList());
        if (StrUtil.isBlank(coreClassList)) {
            return arr;
        }
        try {
            if (coreClassList.startsWith("{")) {
                JSONObject obj = JSONUtil.parseObj(coreClassList);
                for (String k : obj.keySet()) {
                    if (StrUtil.isNotBlank(k)) arr.add(k);
                }
                return arr;
            }
            if (coreClassList.startsWith("[")) {
                JSONArray src = JSONUtil.parseArray(coreClassList);
                for (Object it : src) {
                    String one = trim(it);
                    if (StrUtil.isNotBlank(one)) arr.add(one);
                }
            }
        } catch (Exception ignored) {
        }
        return arr;
    }

    private JSONArray deriveTestDatasets(TaskDataset row) {
        JSONArray arr = new JSONArray();
        for (String one : splitUnderscore(row.getCoreName())) {
            if (StrUtil.isNotBlank(one)) {
                arr.add(one);
            }
        }
        return arr;
    }

    private JSONArray parseJsonArrayOrDefault(Object raw, JSONArray dft) {
        if (raw instanceof JSONArray ja) {
            return ja;
        }
        String txt = trim(raw);
        if (StrUtil.isBlank(txt)) {
            return dft;
        }
        try {
            return JSONUtil.parseArray(txt);
        } catch (Exception e) {
            return dft;
        }
    }

    private JSONObject parseJsonObjectOrDefault(Object raw, JSONObject dft) {
        if (raw instanceof JSONObject jo) {
            return jo;
        }
        if (raw instanceof Map<?, ?> map) {
            return JSONUtil.parseObj(map);
        }
        String txt = trim(raw);
        if (StrUtil.isBlank(txt)) {
            return dft;
        }
        try {
            return JSONUtil.parseObj(txt);
        } catch (Exception e) {
            return dft;
        }
    }

    private LocalDateTime parseTime(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> toStringList(Object obj) {
        List<String> out = new ArrayList<>();
        if (obj instanceof List<?> list) {
            for (Object it : list) {
                String s = trim(it);
                if (StrUtil.isNotBlank(s)) out.add(s);
            }
        }
        return out;
    }

    private List<String> jsonArrayToList(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (Object it : arr) {
            String s = trim(it);
            if (StrUtil.isNotBlank(s)) out.add(s);
        }
        return out;
    }

    private JSONObject normalizeMappingRules(Object src) {
        JSONObject result = new JSONObject();
        if (!(src instanceof Map<?, ?> outer)) return result;
        for (Map.Entry<?, ?> entry : outer.entrySet()) {
            String datasetName = trim(entry.getKey());
            if (StrUtil.isBlank(datasetName) || !(entry.getValue() instanceof Map<?, ?> inner)) continue;
            JSONObject datasetMapping = new JSONObject();
            for (Map.Entry<?, ?> mapping : inner.entrySet()) {
                String from = trim(mapping.getKey());
                String to = trim(mapping.getValue());
                if (StrUtil.isBlank(from) || StrUtil.isBlank(to)) continue;
                datasetMapping.set(from, to);
            }
            result.set(datasetName, datasetMapping);
        }
        return result;
    }

    private String currentUsername() {
        try {
            User user = SessionUtil.getCurUser();
            return user != null ? user.getUsername() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void carryExportMeta(JSONObject src, JSONObject dst) {
        dst.set("last_export_time", src.getStr("last_export_time", ""));
        dst.set("last_export_source_updated_time", src.getStr("last_export_source_updated_time", ""));
        dst.set("last_export_by", src.getStr("last_export_by", ""));
        dst.set("last_export_mid_count", src.getInt("last_export_mid_count", 0));
    }

    private String resolveExportStatusCode(JSONObject task) {
        String updated = task.getStr("updated_time", "");
        String lastExportTime = task.getStr("last_export_time", "");
        String exportSnapshot = task.getStr("last_export_source_updated_time", "");
        if (StrUtil.isBlank(lastExportTime)) return "never_exported";
        if (!StrUtil.equals(updated, exportSnapshot)) return "stale";
        return "ready";
    }

    private String statusText(String code) {
        if (StrUtil.equals(code, "ready")) return "已就绪";
        if (StrUtil.equals(code, "stale")) return "未更新";
        return "未导出";
    }

    private List<String> buildDefaultTestPlan(TaskDataset taskDataset) {
        List<String> testPlan = new ArrayList<>();
        List<Long> coreIds = new ArrayList<>();
        for (String id : splitIds(taskDataset.getCoreId())) {
            Long one = safeParseLong(id);
            if (one != null) {
                coreIds.add(one);
            }
        }
        if (coreIds.isEmpty()) return testPlan;

        List<OriginalDataset> originals = originalDatasetService.listByIds(coreIds);
        for (OriginalDataset original : originals) {
            List<String> testTaskIds = extractTestTaskIds(original.getTaskName(), original.getTaskId());
            for (String taskId : testTaskIds) {
                testPlan.add(original.getId() + "_" + taskId);
            }
        }
        return testPlan;
    }

    private List<String> extractTestTaskIds(String taskName, String taskId) {
        List<String> out = new ArrayList<>();
        String[] ids = splitUnderscore(taskId);
        String[] names = splitUnderscore(taskName);
        if (ids.length == 0 || names.length == 0) return out;

        boolean oneToOne = names.length == ids.length;
        if (oneToOne) {
            for (int i = 0; i < ids.length; i++) {
                if (names[i].toLowerCase(Locale.ROOT).contains("test")) {
                    out.add(ids[i]);
                }
            }
            return out;
        }

        for (int i = 0; i < ids.length; i++) {
            int idx = i * 2;
            if (idx + 1 >= names.length) break;
            String prefix = names[idx];
            if ("test".equalsIgnoreCase(prefix)) {
                out.add(ids[i]);
            }
        }
        return out;
    }

    private String[] splitUnderscore(String src) {
        if (StrUtil.isBlank(src)) return new String[0];
        return Arrays.stream(src.split("_"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toArray(String[]::new);
    }

    private List<String> splitIds(String src) {
        if (StrUtil.isBlank(src)) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String one : splitUnderscore(src)) {
            if (StrUtil.isNotBlank(one)) out.add(one);
        }
        return out;
    }

    private TaskDataset findTaskDatasetForExport(String taskName, JSONObject taskDef) {
        TaskDataset exact = taskDatasetService.lambdaQuery()
                .eq(TaskDataset::getName, taskName)
                .orderByDesc(TaskDataset::getId)
                .last("limit 1")
                .one();
        if (exact != null) return exact;

        List<TaskDataset> candidates = taskDatasetService.lambdaQuery()
                .orderByDesc(TaskDataset::getId)
                .list();

        // 兜底1：名字模糊匹配（解决 dev 名称与 task_dataset 名称不完全一致，如 11111/1111）。
        TaskDataset fuzzyByName = findByNameSimilarity(taskName, candidates);
        if (fuzzyByName != null) {
            return fuzzyByName;
        }

        // 兜底2：按测试数据集覆盖匹配（优先与 dev 任务定义语义一致）。
        List<String> requiredNames = jsonArrayToList(taskDef.getJSONArray("test_datasets"));
        if (requiredNames.isEmpty()) return null;
        for (TaskDataset candidate : candidates) {
            Set<String> candidateNames = resolveTaskDatasetOriginalNames(candidate);
            if (!candidateNames.isEmpty() && candidateNames.containsAll(requiredNames)) {
                return candidate;
            }
        }
        return null;
    }

    private TaskDataset findByNameSimilarity(String taskName, List<TaskDataset> candidates) {
        String normDev = normalizeName(taskName);
        if (StrUtil.isBlank(normDev)) return null;
        for (TaskDataset candidate : candidates) {
            String name = candidate.getName();
            String normCandidate = normalizeName(name);
            if (StrUtil.isBlank(normCandidate)) continue;
            if (StrUtil.equals(normDev, normCandidate)) return candidate;
            if (normDev.contains(normCandidate) || normCandidate.contains(normDev)) return candidate;
            if (name != null && (name.equals(taskName) || name.contains(taskName) || taskName.contains(name))) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeName(String src) {
        if (StrUtil.isBlank(src)) return "";
        String lower = src.trim().toLowerCase(Locale.ROOT);
        return lower.replaceAll("[^0-9a-z\\u4e00-\\u9fa5]+", "");
    }

    private Set<String> resolveTaskDatasetOriginalNames(TaskDataset taskDataset) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String id : splitIds(taskDataset.getCoreId())) {
            Long one = safeParseLong(id);
            if (one != null) ids.add(one);
        }
        for (String id : splitIds(taskDataset.getSupId())) {
            Long one = safeParseLong(id);
            if (one != null) ids.add(one);
        }
        if (ids.isEmpty()) return new LinkedHashSet<>();
        List<OriginalDataset> originals = originalDatasetService.listByIds(new ArrayList<>(ids));
        Set<String> names = new LinkedHashSet<>();
        for (OriginalDataset original : originals) {
            if (StrUtil.isNotBlank(original.getName())) {
                names.add(original.getName());
            }
        }
        return names;
    }

    private MappingStatus assessMappingStatus(JSONObject task) {
        List<String> targetSchema = jsonArrayToList(task.getJSONArray("target_schema"));
        Set<String> targetSet = new LinkedHashSet<>(targetSchema);
        List<String> testDatasets = jsonArrayToList(task.getJSONArray("test_datasets"));
        JSONObject mappingRules = task.getJSONObject("mapping_rules");

        if (targetSet.isEmpty()) {
            return new MappingStatus("error", "映射错误", "目标类别为空，请先配置目标类别。", false);
        }
        if (testDatasets.isEmpty()) {
            return new MappingStatus("error", "映射错误", "测试数据集为空，请先选择测试数据集。", false);
        }

        List<String> errors = new ArrayList<>();
        Set<String> incomingTargets = new LinkedHashSet<>();
        for (String datasetName : testDatasets) {
            JSONObject oneMap = mappingRules != null ? mappingRules.getJSONObject(datasetName) : null;
            if (oneMap == null) continue;

            List<String> illegalTarget = new ArrayList<>();
            for (String cls : oneMap.keySet()) {
                String mapped = oneMap.getStr(cls, "");
                if (StrUtil.isBlank(mapped)) continue;
                if (!targetSet.contains(mapped)) {
                    illegalTarget.add(cls + "->" + mapped);
                    continue;
                }
                incomingTargets.add(mapped);
            }
            if (!illegalTarget.isEmpty()) {
                errors.add("数据集 " + datasetName + " 映射目标非法: " + String.join(", ", illegalTarget));
            }
        }

        List<String> uncoveredTargets = new ArrayList<>();
        for (String target : targetSchema) {
            if (!incomingTargets.contains(target)) {
                uncoveredTargets.add(target);
            }
        }
        if (!uncoveredTargets.isEmpty()) {
            errors.add("以下目标类别暂无映射来源: " + String.join(", ", uncoveredTargets));
        }

        if (errors.isEmpty()) {
            return new MappingStatus("ok", "映射正确", "所有目标类别均有至少一个映射来源（允许多对一）。", true);
        }
        return new MappingStatus("error", "映射错误", String.join("；", errors), false);
    }

    private Long safeParseLong(String src) {
        try {
            return Long.parseLong(src);
        } catch (Exception e) {
            return null;
        }
    }

    private List<DatasetSource> resolveDatasetSources(List<String> datasetNames) {
        List<DatasetSource> out = new ArrayList<>();
        if (datasetNames == null || datasetNames.isEmpty()) return out;
        Map<String, String> externalMap = readExternalRegistryMap();
        List<OriginalDataset> allOriginals = originalDatasetService.getAllOriginalDatasets();

        for (String name : datasetNames) {
            String n = trim(name);
            if (StrUtil.isBlank(n)) continue;
            String ext = externalMap.get(n);
            if (StrUtil.isNotBlank(ext)) {
                Path root = normalizeDatasetRoot(Paths.get(ext));
                if (root != null) {
                    out.add(new DatasetSource(n, root));
                    continue;
                }
            }
            OriginalDataset latest = findLatestOriginalByName(allOriginals, n);
            if (latest != null) {
                Path root = normalizeDatasetRoot(parsePath(latest.getDataPath()));
                if (root != null) {
                    out.add(new DatasetSource(n, root));
                }
            }
        }
        return out;
    }

    private Map<String, String> readExternalRegistryMap() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Path file = Paths.get(originalDatasetRoot).normalize().resolve("external_dataset_registry.json");
            if (!Files.exists(file)) return out;
            String txt = Files.readString(file, StandardCharsets.UTF_8);
            if (StrUtil.isBlank(txt)) return out;
            JSONObject obj = JSONUtil.parseObj(txt);
            JSONArray arr = obj.getJSONArray("datasets");
            if (arr == null) return out;
            for (Object item : arr) {
                if (!(item instanceof JSONObject jo)) continue;
                String name = trim(jo.get("name"));
                String path = trim(jo.get("path"));
                if (StrUtil.isNotBlank(name) && StrUtil.isNotBlank(path)) {
                    out.put(name, path);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private OriginalDataset findLatestOriginalByName(List<OriginalDataset> list, String name) {
        OriginalDataset latest = null;
        if (list == null) return null;
        for (OriginalDataset one : list) {
            if (!StrUtil.equals(trim(one.getName()), name)) continue;
            if (latest == null) {
                latest = one;
                continue;
            }
            long cur = one.getId() == null ? -1L : one.getId();
            long old = latest.getId() == null ? -1L : latest.getId();
            if (cur > old) latest = one;
        }
        return latest;
    }

    private Path parsePath(String raw) {
        String s = trim(raw);
        if (StrUtil.isBlank(s)) return null;
        return Paths.get(s).normalize();
    }

    private Path normalizeDatasetRoot(Path p) {
        if (p == null) return null;
        if (!Files.exists(p)) return null;
        Path n = p.normalize();
        String tail = n.getFileName() != null ? n.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        if ("images".equals(tail) || "annotations".equals(tail)) {
            Path parent = n.getParent();
            return parent != null && Files.isDirectory(parent) ? parent : null;
        }
        return Files.isDirectory(n) ? n : null;
    }

    private int exportFromDatasetSources(String taskName, JSONObject taskDef, List<DatasetSource> sources) throws Exception {
        List<String> target = jsonArrayToList(taskDef.getJSONArray("target_schema"));
        JSONObject mappingRules = taskDef.getJSONObject("mapping_rules");
        LinkedHashMap<String, Long> mappedTargetCounts = new LinkedHashMap<>();
        for (String one : target) {
            if (StrUtil.isNotBlank(one)) mappedTargetCounts.put(one, 0L);
        }
        int exportedCount = 0;
        String exportName = taskName;
        Path outRoot = Paths.get(instanceDatasetMidRoot.trim().replaceAll("/+$", ""), exportName).normalize();
        clearExportedMidByTask(taskName);
        Files.createDirectories(outRoot);
        Path outTrainImg = outRoot.resolve("train").resolve("images");
        Path outTrainAnno = outRoot.resolve("train").resolve("anno");
        Path outTestImg = outRoot.resolve("test").resolve("images");
        Path outTestAnno = outRoot.resolve("test").resolve("anno");
        Files.createDirectories(outTrainImg);
        Files.createDirectories(outTrainAnno);
        Files.createDirectories(outTestImg);
        Files.createDirectories(outTestAnno);

        int trainImgCnt = 0;
        int trainAnnoCnt = 0;
        int testImgCnt = 0;
        int testAnnoCnt = 0;
        for (DatasetSource source : sources) {
            ResolvedExportPaths paths = resolveExportPaths(source.root);
            if (paths == null) continue;
            JSONObject oneMap = mappingRules != null ? mappingRules.getJSONObject(source.datasetName) : null;
            Map<String, String> labelMap = new LinkedHashMap<>();
            if (oneMap != null) {
                for (String k : oneMap.keySet()) {
                    String mapped = trim(oneMap.getStr(k, ""));
                    if (StrUtil.isBlank(mapped)) continue;
                    labelMap.put(k, mapped);
                }
            }
            if (labelMap.isEmpty()) {
                continue;
            }
            ExportStat trainStat = exportSplitByMappedLabels(paths.trainImages, paths.trainAnnos, outTrainImg, outTrainAnno, labelMap, mappedTargetCounts);
            ExportStat testStat = exportSplitByMappedLabels(paths.testImages, paths.testAnnos, outTestImg, outTestAnno, labelMap, mappedTargetCounts);
            trainImgCnt += trainStat.imageCount;
            trainAnnoCnt += trainStat.annoCount;
            testImgCnt += testStat.imageCount;
            testAnnoCnt += testStat.annoCount;
            if (trainStat.imageCount + testStat.imageCount > 0) {
                exportedCount++;
            }
        }
        if (exportedCount > 0) {
            InstanceDatasetMid mid = new InstanceDatasetMid();
            mid.setFatherName(taskName);
            mid.setName(exportName);
            mid.setSensorType("外部");
            mid.setTargetType("复合");
            mid.setDataFormat(0);
            mid.setClassList(JSONUtil.toJsonStr(mappedTargetCounts));
            mid.setClassNum(target.size());
            int imgTotal = trainImgCnt + testImgCnt;
            mid.setImgNum(imgTotal);
            mid.setAnnoNum(trainAnnoCnt + testAnnoCnt);
            mid.setTrainImagePath(toPosix(outTrainImg));
            mid.setTrainAnnoPath(toPosix(outTrainAnno));
            mid.setTestImagePath(toPosix(outTestImg));
            mid.setTestAnnoPath(toPosix(outTestAnno));
            mid.setUsername(currentUsername());
            mid.setCreatedTime(LocalDateTime.now());
            mid.setUpdatedTime(LocalDateTime.now());
            saveMidRecord(mid);
        }
        return exportedCount;
    }

    private ExportStat exportSplitByMappedLabels(
            Path srcImgDir,
            Path srcAnnoDir,
            Path dstImgDir,
            Path dstAnnoDir,
            Map<String, String> labelMap,
            Map<String, Long> mappedTargetCounts) throws IOException {
        if (srcImgDir == null || srcAnnoDir == null || !Files.isDirectory(srcImgDir) || !Files.isDirectory(srcAnnoDir)) {
            return new ExportStat();
        }
        Files.createDirectories(dstImgDir);
        Files.createDirectories(dstAnnoDir);
        ExportStat coco = tryExportCocoByMappedLabels(srcImgDir, srcAnnoDir, dstImgDir, dstAnnoDir, labelMap, mappedTargetCounts);
        if (coco != null) {
            return coco;
        }
        // 非 COCO 结构暂按原有逻辑兜底（后续可继续扩展 DOTA/YOLO 的按标签过滤）
        ExportStat fallback = new ExportStat();
        fallback.imageCount = copyDirContent(srcImgDir, dstImgDir);
        fallback.annoCount = copyDirContent(srcAnnoDir, dstAnnoDir);
        return fallback;
    }

    private ExportStat tryExportCocoByMappedLabels(
            Path srcImgDir,
            Path srcAnnoDir,
            Path dstImgDir,
            Path dstAnnoDir,
            Map<String, String> labelMap,
            Map<String, Long> mappedTargetCounts) throws IOException {
        List<Path> jsonFiles = new ArrayList<>();
        collectFilesBySuffix(srcAnnoDir, ".json", jsonFiles);
        if (jsonFiles.isEmpty()) {
            return null;
        }

        ExportStat stat = new ExportStat();
        Set<String> copiedImageRelPaths = new HashSet<>();
        for (Path oneJson : jsonFiles) {
            JSONObject root;
            try {
                root = JSONUtil.parseObj(Files.readString(oneJson, StandardCharsets.UTF_8));
            } catch (Exception e) {
                continue;
            }
            JSONArray images = root.getJSONArray("images");
            JSONArray annotations = root.getJSONArray("annotations");
            JSONArray categories = root.getJSONArray("categories");
            if (images == null || annotations == null || categories == null) {
                continue;
            }

            LinkedHashMap<String, Integer> mappedCategoryIdByName = new LinkedHashMap<>();
            Map<Integer, Integer> oldCatIdToNewCatId = new HashMap<>();
            for (Object cObj : categories) {
                if (!(cObj instanceof JSONObject c)) continue;
                Integer id = c.getInt("id");
                String n = c.getStr("name", "");
                if (id == null || StrUtil.isBlank(n)) continue;
                String mapped = labelMap.get(n);
                if (StrUtil.isBlank(mapped)) continue;
                mappedCategoryIdByName.computeIfAbsent(mapped, k -> mappedCategoryIdByName.size() + 1);
                oldCatIdToNewCatId.put(id, mappedCategoryIdByName.get(mapped));
            }
            if (mappedCategoryIdByName.isEmpty()) {
                continue;
            }

            Set<Long> keepImageIds = new HashSet<>();
            List<JSONObject> candidateAnn = new ArrayList<>();
            for (Object aObj : annotations) {
                if (!(aObj instanceof JSONObject a)) continue;
                Integer catId = a.getInt("category_id");
                if (catId == null) continue;
                if (!oldCatIdToNewCatId.containsKey(catId)) continue;
                Long imageId = a.getLong("image_id");
                if (imageId != null) {
                    keepImageIds.add(imageId);
                }
                candidateAnn.add(a);
            }
            if (keepImageIds.isEmpty()) {
                continue;
            }

            JSONArray keptImages = new JSONArray();
            Set<Long> copiedImageIds = new HashSet<>();
            for (Object iObj : images) {
                if (!(iObj instanceof JSONObject i)) continue;
                Long id = i.getLong("id");
                if (id == null || !keepImageIds.contains(id)) continue;
                String fileName = i.getStr("file_name", "");
                if (StrUtil.isBlank(fileName)) continue;
                Path srcImage = srcImgDir.resolve(fileName).normalize();
                if (!Files.isRegularFile(srcImage)) {
                    continue;
                }
                Path dstImage = dstImgDir.resolve(fileName).normalize();
                Files.createDirectories(dstImage.getParent());
                Files.copy(srcImage, dstImage, StandardCopyOption.REPLACE_EXISTING);
                keptImages.add(i);
                copiedImageIds.add(id);
                String rel = fileName.replace("\\", "/");
                if (copiedImageRelPaths.add(rel)) {
                    stat.imageCount++;
                }
            }
            if (copiedImageIds.isEmpty()) {
                continue;
            }

            Map<Integer, String> newCatIdToName = new HashMap<>();
            for (Map.Entry<String, Integer> e : mappedCategoryIdByName.entrySet()) {
                newCatIdToName.put(e.getValue(), e.getKey());
            }
            JSONArray keptAnn = new JSONArray();
            for (JSONObject a : candidateAnn) {
                Long imageId = a.getLong("image_id");
                if (imageId == null || !copiedImageIds.contains(imageId)) continue;
                JSONObject one = JSONUtil.parseObj(a);
                Integer oldCatId = one.getInt("category_id");
                Integer newCatId = oldCatId == null ? null : oldCatIdToNewCatId.get(oldCatId);
                if (newCatId == null) continue;
                one.set("category_id", newCatId);
                keptAnn.add(one);
                stat.annoCount++;
                String mappedTarget = newCatIdToName.getOrDefault(newCatId, "");
                if (StrUtil.isNotBlank(mappedTarget) && mappedTargetCounts.containsKey(mappedTarget)) {
                    mappedTargetCounts.put(mappedTarget, mappedTargetCounts.get(mappedTarget) + 1);
                }
            }

            JSONArray keptCategories = new JSONArray();
            for (Map.Entry<String, Integer> e : mappedCategoryIdByName.entrySet()) {
                JSONObject c = new JSONObject();
                c.set("id", e.getValue());
                c.set("name", e.getKey());
                keptCategories.add(c);
            }

            JSONObject out = new JSONObject(new LinkedHashMap<>());
            for (String k : root.keySet()) {
                if ("images".equals(k) || "annotations".equals(k) || "categories".equals(k)) continue;
                out.set(k, root.get(k));
            }
            out.set("images", keptImages);
            out.set("annotations", keptAnn);
            out.set("categories", keptCategories);

            Path relJson = srcAnnoDir.relativize(oneJson);
            Path dstJson = dstAnnoDir.resolve(relJson);
            Files.createDirectories(dstJson.getParent());
            Files.writeString(dstJson, JSONUtil.toJsonPrettyStr(out), StandardCharsets.UTF_8);
        }
        return stat;
    }

    private void collectFilesBySuffix(Path dir, String suffix, List<Path> out) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    collectFilesBySuffix(child, suffix, out);
                    continue;
                }
                String n = child.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.endsWith(suffix)) out.add(child);
            }
        }
    }

    private ResolvedExportPaths resolveExportPaths(Path datasetRoot) {
        if (datasetRoot == null || !Files.isDirectory(datasetRoot)) return null;
        Path images = datasetRoot.resolve("images");
        Path annos = datasetRoot.resolve("annotations");
        Path trainImages = datasetRoot.resolve("train").resolve("images");
        Path trainAnnos = datasetRoot.resolve("train").resolve("anno");
        Path testImages = datasetRoot.resolve("test").resolve("images");
        Path testAnnos = datasetRoot.resolve("test").resolve("anno");

        if (Files.isDirectory(trainImages) && Files.isDirectory(trainAnnos)) {
            return new ResolvedExportPaths(
                    trainImages, trainAnnos,
                    Files.isDirectory(testImages) ? testImages : null,
                    Files.isDirectory(testAnnos) ? testAnnos : null
            );
        }
        if (Files.isDirectory(images) && Files.isDirectory(annos)) {
            Path trainSplitImg = images.resolve("train");
            Path trainSplitAnno = annos.resolve("train");
            Path testSplitImg = images.resolve("test");
            Path testSplitAnno = annos.resolve("test");
            if (Files.isDirectory(trainSplitImg) && Files.isDirectory(trainSplitAnno)) {
                return new ResolvedExportPaths(
                        trainSplitImg, trainSplitAnno,
                        Files.isDirectory(testSplitImg) ? testSplitImg : null,
                        Files.isDirectory(testSplitAnno) ? testSplitAnno : null
                );
            }
            return new ResolvedExportPaths(images, annos, null, null);
        }
        return null;
    }

    private int copyDirContent(Path srcDir, Path dstDir) throws IOException {
        if (srcDir == null || !Files.isDirectory(srcDir)) return 0;
        Files.createDirectories(dstDir);
        int copied = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(srcDir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    copied += copyDirContent(child, dstDir.resolve(child.getFileName().toString()));
                    continue;
                }
                Files.copy(child, dstDir.resolve(child.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
        }
        return copied;
    }

    /** 是否存在可清除的中间导出（中间表记录或 instance_dataset_mid 下对应目录） */
    private boolean hasExportedMidArtifacts(String taskName) throws IOException {
        if (StrUtil.isBlank(taskName)) return false;
        if (countMidByFatherName(taskName) > 0) return true;
        Path root = Paths.get(instanceDatasetMidRoot.trim().replaceAll("/+$", "")).normalize();
        if (!Files.isDirectory(root)) return false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) continue;
                String folder = child.getFileName().toString();
                if (folder.equals(taskName) || folder.startsWith(taskName + "_")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void clearExportedMidByTask(String taskName) throws IOException {
        if (StrUtil.isBlank(taskName)) return;
        String table = resolveMidTableName();
        if (StrUtil.isNotBlank(table)) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE father_name = ?", taskName);
        }
        Path root = Paths.get(instanceDatasetMidRoot.trim().replaceAll("/+$", "")).normalize();
        if (!Files.isDirectory(root)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) continue;
                String folder = child.getFileName().toString();
                if (folder.equals(taskName) || folder.startsWith(taskName + "_")) {
                    deleteDirectoryRecursively(child);
                }
            }
        }
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String toPosix(Path p) {
        String s = p.normalize().toString().replace("\\", "/");
        return s.endsWith("/") ? s : s + "/";
    }

    private void saveMidRecord(InstanceDatasetMid mid) {
        String table = resolveMidTableName();
        if (StrUtil.equals(table, "instance_dataset_mid")) {
            instanceDatasetMidService.save(mid);
            return;
        }
        if (StrUtil.equals(table, "instance_dataset")) {
            String sql = "INSERT INTO instance_dataset " +
                    "(father_name, name, sensor_type, target_type, img_num, anno_num, class_num, class_list, " +
                    "train_image_path, train_anno_path, test_image_path, test_anno_path, data_format, username, created_time, updated_time) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            jdbcTemplate.update(sql,
                    mid.getFatherName(),
                    mid.getName(),
                    mid.getSensorType(),
                    mid.getTargetType(),
                    mid.getImgNum(),
                    mid.getAnnoNum(),
                    mid.getClassNum(),
                    mid.getClassList(),
                    mid.getTrainImagePath(),
                    mid.getTrainAnnoPath(),
                    mid.getTestImagePath(),
                    mid.getTestAnnoPath(),
                    mid.getDataFormat(),
                    mid.getUsername(),
                    mid.getCreatedTime(),
                    mid.getUpdatedTime());
            return;
        }
        throw new IllegalStateException("未找到可用中间实例数据集表（instance_dataset_mid 或 instance_dataset）");
    }

    private String resolveMidTableName() {
        if (tableExists("instance_dataset_mid")) return "instance_dataset_mid";
        if (tableExists("instance_dataset")) return "instance_dataset";
        return "";
    }

    private boolean tableExists(String tableName) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class,
                    tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static class DatasetSource {
        final String datasetName;
        final Path root;

        DatasetSource(String datasetName, Path root) {
            this.datasetName = datasetName;
            this.root = root;
        }
    }

    private static class ExportStat {
        int imageCount;
        int annoCount;
    }

    private static class ResolvedExportPaths {
        final Path trainImages;
        final Path trainAnnos;
        final Path testImages;
        final Path testAnnos;

        ResolvedExportPaths(Path trainImages, Path trainAnnos, Path testImages, Path testAnnos) {
            this.trainImages = trainImages;
            this.trainAnnos = trainAnnos;
            this.testImages = testImages;
            this.testAnnos = testAnnos;
        }
    }

    private static class MappingStatus {
        final String code;
        final String text;
        final String detail;
        final boolean ok;

        MappingStatus(String code, String text, String detail, boolean ok) {
            this.code = code;
            this.text = text;
            this.detail = detail;
            this.ok = ok;
        }
    }

    private int countMidByFatherName(String name) {
        String table = resolveMidTableName();
        if (StrUtil.isBlank(table)) return 0;
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM " + table + " WHERE father_name = ?",
                    Integer.class,
                    name);
            return cnt == null ? 0 : cnt;
        } catch (Exception e) {
            return 0;
        }
    }

    private String trim(Object obj) {
        return obj == null ? "" : StrUtil.trimToEmpty(String.valueOf(obj));
    }
}
