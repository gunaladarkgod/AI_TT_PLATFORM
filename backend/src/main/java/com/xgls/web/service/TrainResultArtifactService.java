package com.xgls.web.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xgls.web.entity.TrainResult;
import com.xgls.web.entity.TrainTask;
import com.xgls.web.utils.TrainConfigPathUtil;
import com.xgls.web.utils.WorkspacePathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理每一次训练结果自身的本地文件：配置快照及可编辑的名称、备注、标签。
 * 这些信息存放在 artifacts/result_metadata 中，不依赖数据库字段。
 */
@Slf4j
@Service
public class TrainResultArtifactService {

    private Path artifactsRoot() {
        return WorkspacePathUtil.workspaceRoot().resolve("artifacts").toAbsolutePath().normalize();
    }

    private Path metadataRoot() {
        return artifactsRoot().resolve("result_metadata").normalize();
    }

    private Path metadataFile(Integer resultId) {
        if (resultId == null || resultId <= 0) return null;
        return metadataRoot().resolve(resultId + ".json").normalize();
    }

    /** 在 Runner 成功返回后复制 config.py，并生成结果元数据。 */
    public void createSnapshot(TrainResult result, TrainTask task, JSONObject runnerPayload) {
        if (result == null || result.getId() == null) return;
        JSONObject meta = readMeta(result);
        meta.set("resultId", result.getId());
        meta.set("taskId", result.getTaskId());
        meta.set("taskName", result.getTaskName());
        String engine = runnerPayload == null ? null : runnerPayload.getStr("engine");
        meta.set("engine", StrUtil.blankToDefault(engine, "mmdet"));
        meta.set("resultName", StrUtil.blankToDefault(meta.getStr("resultName"), result.getTaskName()));
        meta.set("remark", StrUtil.blankToDefault(meta.getStr("remark"), ""));
        if (!(meta.get("tags") instanceof JSONArray)) meta.set("tags", new ArrayList<String>());
        meta.set("createdAt", StrUtil.blankToDefault(meta.getStr("createdAt"), LocalDateTime.now().toString()));

        Path workDir = resolveRunnerWorkDir(runnerPayload == null ? null : runnerPayload.getStr("work_dir"));
        if (workDir == null) {
            meta.set("configStatus", "Runner 未返回有效结果目录，未能复制配置快照");
            writeMeta(meta);
            return;
        }
        meta.set("resultDir", artifactsRoot().relativize(workDir).toString().replace("\\", "/"));
        meta.set("weights", normalizeWeights(runnerPayload == null ? null : runnerPayload.getJSONArray("weights")));
        meta.set("weightStatus", runnerPayload == null ? "unknown" :
                StrUtil.blankToDefault(runnerPayload.getStr("weight_status"), "missing"));

        if (!"mmdet".equalsIgnoreCase(meta.getStr("engine"))) {
            meta.set("configStatus", "当前引擎不使用 MMDet config.py 配置快照");
            writeMeta(meta);
            return;
        }

        Path configSource = TrainConfigPathUtil.findExistingConfig(
                WorkspacePathUtil.workspaceRoot().resolve("engines").resolve("mmdet_run").resolve("myfiles"), task.getName());
        if (configSource == null) {
            meta.set("configStatus", "当前任务没有可复制的 config.py（自定义 fixed 任务可不使用 MMDet 配置）");
            writeMeta(meta);
            return;
        }
        try {
            Path target = workDir.resolve("config.py").normalize();
            if (!target.startsWith(workDir)) throw new IOException("配置副本路径越界");
            Files.copy(configSource, target, StandardCopyOption.REPLACE_EXISTING);
            meta.set("configPath", artifactsRoot().relativize(target).toString().replace("\\", "/"));
            meta.set("configStatus", "copied");
        } catch (IOException e) {
            meta.set("configStatus", "复制配置快照失败：" + e.getMessage());
            log.warn("copy result config snapshot failed, resultId={}, task={}", result.getId(), task.getName(), e);
        }
        writeMeta(meta);
    }

    public void mergeMetadata(TrainResult result) {
        if (result == null) return;
        JSONObject meta = readMeta(result);
        result.setResultName(StrUtil.blankToDefault(meta.getStr("resultName"), result.getTaskName()));
        result.setRemark(meta.getStr("remark", ""));
        result.setTags(readTags(meta.get("tags")));
        result.setConfigPath(meta.getStr("configPath"));
    }

    private List<Map<String, String>> normalizeWeights(JSONArray rawWeights) {
        List<Map<String, String>> weights = new ArrayList<>();
        if (rawWeights == null) return weights;
        for (Object item : rawWeights) {
            if (!(item instanceof JSONObject weight)) continue;
            String relative = StrUtil.trim(weight.getStr("path"));
            if (StrUtil.isBlank(relative)) continue;
            try {
                Path path = artifactsRoot().resolve(relative).normalize();
                if (!path.startsWith(artifactsRoot()) || !Files.isRegularFile(path)) continue;
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("role", StrUtil.blankToDefault(weight.getStr("role"), "checkpoint"));
                entry.put("path", artifactsRoot().relativize(path).toString().replace("\\", "/"));
                weights.add(entry);
            } catch (Exception ignored) {
            }
        }
        return weights;
    }

    public Map<String, Object> detail(TrainResult result) {
        mergeMetadata(result);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", result);
        JSONObject meta = readMeta(result);
        out.put("resultDir", meta.getStr("resultDir"));
        out.put("configPath", meta.getStr("configPath"));
        out.put("configStatus", meta.getStr("configStatus"));
        out.put("engine", meta.getStr("engine"));
        out.put("weights", meta.getJSONArray("weights"));
        out.put("weightStatus", meta.getStr("weightStatus"));
        return out;
    }

    public String resultDirectory(Integer resultId) {
        if (resultId == null) return null;
        JSONObject meta = readMeta(resultId);
        String stored = StrUtil.trim(meta.getStr("resultDir"));
        if (StrUtil.isBlank(stored)) return null;
        try {
            Path relative = Path.of(stored);
            Path resolved = (relative.isAbsolute() ? relative : artifactsRoot().resolve(relative))
                    .toAbsolutePath().normalize();
            if (resolved.startsWith(artifactsRoot()) && Files.isDirectory(resolved)) return resolved.toString();
        } catch (Exception ignored) {
        }
        return null;
    }

    public Map<String, Object> updateMetadata(TrainResult result, String resultName, String remark, Collection<?> tags) {
        JSONObject meta = readMeta(result);
        String safeName = StrUtil.trim(resultName);
        meta.set("resultId", result.getId());
        meta.set("taskId", result.getTaskId());
        meta.set("taskName", result.getTaskName());
        meta.set("resultName", StrUtil.blankToDefault(safeName, result.getTaskName()));
        meta.set("remark", StrUtil.nullToEmpty(remark).trim());
        meta.set("tags", normalizeTags(tags));
        meta.set("updatedAt", LocalDateTime.now().toString());
        writeMeta(meta);
        return detail(result);
    }

    public Map<String, Object> readConfigSnapshot(TrainResult result, boolean includeText) throws IOException {
        JSONObject meta = readMeta(result);
        String stored = StrUtil.trim(meta.getStr("configPath"));
        if (StrUtil.isBlank(stored)) {
            throw new IOException(StrUtil.blankToDefault(meta.getStr("configStatus"),
                    "该结果没有配置快照。旧结果需要重新训练后才会自动生成快照。"));
        }
        Path storedPath = Path.of(stored);
        Path path = (storedPath.isAbsolute() ? storedPath : artifactsRoot().resolve(storedPath))
                .toAbsolutePath().normalize();
        if (!path.startsWith(artifactsRoot()) || !Files.isRegularFile(path)) {
            throw new IOException("结果配置快照不存在：" + path);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultId", result.getId());
        out.put("config_path", path.toString().replace("\\", "/"));
        out.put("file_name", path.getFileName().toString());
        if (includeText) out.put("text", Files.readString(path, StandardCharsets.UTF_8));
        return out;
    }

    public void deleteMetadata(Integer resultId) {
        Path file = metadataFile(resultId);
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("delete result metadata failed, resultId={}, detail={}", resultId, e.getMessage());
        }
    }

    private JSONObject readMeta(TrainResult result) {
        return readMeta(result == null ? null : result.getId());
    }

    private JSONObject readMeta(Integer resultId) {
        Path file = metadataFile(resultId);
        if (file != null && Files.isRegularFile(file)) {
            try {
                return JSONUtil.parseObj(Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn("read result metadata failed, resultId={}, detail={}", resultId, e.getMessage());
            }
        }
        return new JSONObject();
    }

    private void writeMeta(JSONObject meta) {
        Integer resultId = meta.getInt("resultId");
        Path file = metadataFile(resultId);
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSONUtil.toJsonPrettyStr(meta), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("保存结果元数据失败：" + e.getMessage(), e);
        }
    }

    private Path resolveRunnerWorkDir(String raw) {
        if (StrUtil.isBlank(raw)) return null;
        try {
            Path path = Path.of(raw).toAbsolutePath().normalize();
            Path artifacts = artifactsRoot();
            return path.startsWith(artifacts) && Files.isDirectory(path) ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readTags(Object raw) {
        if (raw instanceof JSONArray array) return normalizeTags(array);
        if (raw instanceof Collection<?> collection) return normalizeTags(collection);
        return new ArrayList<>();
    }

    private List<String> normalizeTags(Collection<?> tags) {
        List<String> out = new ArrayList<>();
        if (tags == null) return out;
        for (Object item : tags) {
            String tag = StrUtil.trim(item == null ? null : String.valueOf(item));
            if (StrUtil.isBlank(tag) || out.contains(tag)) continue;
            out.add(tag.length() > 32 ? tag.substring(0, 32) : tag);
            if (out.size() >= 12) break;
        }
        return out;
    }
}
