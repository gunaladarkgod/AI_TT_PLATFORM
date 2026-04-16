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

    @Value("${sys.task-dataset-dev-file:/home/omen1/AI_TT_Platform/data/task_dataset_dev/tasks.json}")
    private String taskDatasetDevFile;
    @Value("${sys.original-dataset-root:/home/omen1/AI_TT_Platform/data/original_dataset}")
    private String originalDatasetRoot;
    @Value("${sys.instancecfg.instancedata-mid-root:/home/omen1/AI_TT_Platform/data/instance_dataset_mid/}")
    private String instanceDatasetMidRoot;

    private final TaskDataset1Service taskDatasetService;
    private final OriginalDataset1Service originalDatasetService;
    private final OriginalDatasetService originalDatasetQueryService;
    private final InstanceDatasetMidService instanceDatasetMidService;
    private final JdbcTemplate jdbcTemplate;

    public TaskDatasetDevService(
            TaskDataset1Service taskDatasetService,
            OriginalDataset1Service originalDatasetService,
            OriginalDatasetService originalDatasetQueryService,
            InstanceDatasetMidService instanceDatasetMidService,
            JdbcTemplate jdbcTemplate) {
        this.taskDatasetService = taskDatasetService;
        this.originalDatasetService = originalDatasetService;
        this.originalDatasetQueryService = originalDatasetQueryService;
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
                    out.add(new DatasetSource(root));
                    continue;
                }
            }
            OriginalDataset latest = findLatestOriginalByName(allOriginals, n);
            if (latest != null) {
                Path root = normalizeDatasetRoot(parsePath(latest.getDataPath()));
                if (root != null) {
                    out.add(new DatasetSource(root));
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
        JSONObject mappedTargetCounts = buildMappedTargetClassCounts(taskDef);
        String classListJson = JSONUtil.toJsonStr(mappedTargetCounts);
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
            trainImgCnt += copyDirContent(paths.trainImages, outTrainImg);
            trainAnnoCnt += copyDirContent(paths.trainAnnos, outTrainAnno);
            testImgCnt += copyDirContent(paths.testImages, outTestImg);
            testAnnoCnt += copyDirContent(paths.testAnnos, outTestAnno);
            exportedCount++;
        }
        if (exportedCount > 0) {
            InstanceDatasetMid mid = new InstanceDatasetMid();
            mid.setFatherName(taskName);
            mid.setName(exportName);
            mid.setSensorType("外部");
            mid.setTargetType("复合");
            mid.setDataFormat(0);
            mid.setClassList(classListJson);
            mid.setClassNum(target.size());
            int imgTotal = trainImgCnt + testImgCnt;
            mid.setImgNum(imgTotal);
            // 与图片数一致：COCO 等单 json 标注时按「文件数」统计会得到 1，与真实图像数不符；预处理列表「样本数」与图像数对齐
            mid.setAnnoNum(imgTotal);
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

    /**
     * 按 mapping_rules 将各数据集原始标签样本数累加到目标类别（与任务映射页一致）。
     */
    private JSONObject buildMappedTargetClassCounts(JSONObject taskDef) {
        List<String> targets = jsonArrayToList(taskDef.getJSONArray("target_schema"));
        LinkedHashMap<String, Long> acc = new LinkedHashMap<>();
        for (String t : targets) {
            if (StrUtil.isNotBlank(t)) {
                acc.put(t, 0L);
            }
        }
        List<String> testDatasets = jsonArrayToList(taskDef.getJSONArray("test_datasets"));
        JSONObject mappingRules = taskDef.getJSONObject("mapping_rules");
        if (mappingRules == null || testDatasets.isEmpty()) {
            JSONObject empty = new JSONObject(new LinkedHashMap<>());
            for (String t : targets) {
                if (StrUtil.isNotBlank(t)) {
                    empty.set(t, 0);
                }
            }
            return empty;
        }
        for (String datasetName : testDatasets) {
            JSONObject oneMap = mappingRules.getJSONObject(datasetName);
            if (oneMap == null) {
                continue;
            }
            Map<String, Long> origCounts = originalDatasetQueryService.resolveClassCountMapByDatasetName(datasetName);
            for (String origKey : oneMap.keySet()) {
                String mapped = trim(oneMap.getStr(origKey, ""));
                if (StrUtil.isBlank(mapped) || !acc.containsKey(mapped)) {
                    continue;
                }
                long add = origCounts.getOrDefault(origKey, 0L);
                acc.put(mapped, acc.get(mapped) + add);
            }
        }
        JSONObject out = new JSONObject(new LinkedHashMap<>());
        for (Map.Entry<String, Long> e : acc.entrySet()) {
            out.set(e.getKey(), e.getValue().intValue());
        }
        return out;
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
        final Path root;

        DatasetSource(Path root) {
            this.root = root;
        }
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
