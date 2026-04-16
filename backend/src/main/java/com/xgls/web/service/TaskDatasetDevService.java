package com.xgls.web.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xgls.web.base.AjaxResult;
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

    private String trim(Object obj) {
        return obj == null ? "" : StrUtil.trimToEmpty(String.valueOf(obj));
    }
}
