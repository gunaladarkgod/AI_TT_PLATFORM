package com.xgls.web.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xgls.web.base.AjaxResult;
import com.xgls.web.entity.InstanceDatasetMid;
import com.xgls.web.entity.OriginalDataset;
import com.xgls.web.entity.TaskDataset;
import com.xgls.web.entity.User;
import com.xgls.web.utils.SessionUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TaskDatasetDevService {

    @Value("${sys.task-dataset-dev-file:/home/omen1/AI_TT_Platform/data/task_dataset_dev/tasks.json}")
    private String taskDatasetDevFile;

    private final TaskDataset1Service taskDatasetService;
    private final OriginalDataset1Service originalDatasetService;
    private final InstanceDatasetMidService instanceDatasetMidService;

    public TaskDatasetDevService(
            TaskDataset1Service taskDatasetService,
            OriginalDataset1Service originalDatasetService,
            InstanceDatasetMidService instanceDatasetMidService) {
        this.taskDatasetService = taskDatasetService;
        this.originalDatasetService = originalDatasetService;
        this.instanceDatasetMidService = instanceDatasetMidService;
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

    public AjaxResult deleteTask(String name) {
        String taskName = StrUtil.trimToEmpty(name);
        if (StrUtil.isBlank(taskName)) return AjaxResult.error("缺少任务名称");
        try {
            JSONObject root = readTaskRoot();
            if (!root.containsKey(taskName)) {
                return AjaxResult.error("任务不存在");
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

            TaskDataset taskDataset = taskDatasetService.lambdaQuery()
                    .eq(TaskDataset::getName, name)
                    .orderByDesc(TaskDataset::getId)
                    .last("limit 1")
                    .one();
            if (taskDataset == null) {
                return AjaxResult.error("未找到同名任务数据集，无法导出中间实例数据集");
            }

            List<String> testPlan = buildDefaultTestPlan(taskDataset);
            List<String> trainOriginalIds = splitIds(taskDataset.getSupId());
            taskDatasetService.processTestPlan(taskDataset, testPlan, trainOriginalIds, 1);

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
            out.add(row);
        }
        out.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        return out;
    }

    private JSONObject readTaskRoot() throws Exception {
        Path path = taskConfigPath();
        if (!Files.exists(path)) {
            return new JSONObject(new LinkedHashMap<>());
        }
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (StrUtil.isBlank(text)) {
            return new JSONObject(new LinkedHashMap<>());
        }
        Object obj = JSONUtil.parse(text);
        if (obj instanceof JSONObject jo) {
            return jo;
        }
        return new JSONObject(new LinkedHashMap<>());
    }

    private void writeTaskRoot(JSONObject root) throws Exception {
        Path path = taskConfigPath();
        FileUtil.mkParentDirs(path.toFile());
        Files.writeString(path, JSONUtil.toJsonPrettyStr(root), StandardCharsets.UTF_8);
    }

    private Path taskConfigPath() {
        return Paths.get(taskDatasetDevFile).normalize();
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
        User user = SessionUtil.getCurUser();
        return user != null ? user.getUsername() : "";
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
            coreIds.add(Long.parseLong(id));
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

    private int countMidByFatherName(String name) {
        return Math.toIntExact(instanceDatasetMidService.lambdaQuery()
                .eq(InstanceDatasetMid::getFatherName, name)
                .count());
    }

    private String trim(Object obj) {
        return obj == null ? "" : StrUtil.trimToEmpty(String.valueOf(obj));
    }
}
