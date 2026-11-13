package com.xgls.web.service;

import com.xgls.web.utils.WorkspacePathUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Manages registered B/C repositories in the ignored local-components directory. */
@Service
public class AlgorithmComponentService {
    private final Path root;
    private final ResearchRegistryService registry;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Map<String, Object> task;
    private final List<String> logs = new ArrayList<>();

    @Autowired
    public AlgorithmComponentService(ResearchRegistryService registry) {
        this(WorkspacePathUtil.workspaceRoot(), registry);
    }

    AlgorithmComponentService(Path root, ResearchRegistryService registry) {
        this.root = root.toAbsolutePath().normalize();
        this.registry = registry;
    }

    public synchronized Map<String, Object> list(String kind, String direction) {
        List<Map<String, Object>> entries = entries(kind, direction);
        List<Map<String, Object>> components = entries.stream().map(item -> component(kind, item)).toList();
        return Map.of("components", components, "downloadSupported", gitAvailable(), "task", snapshot());
    }

    public synchronized Map<String, Object> install(String kind, String id) {
        Map<String, Object> item = find(kind, id);
        if (item == null) throw new IllegalArgumentException("未登记的组件：" + id);
        if (task != null && "running".equals(task.get("status"))) throw new IllegalArgumentException("已有组件任务正在执行");
        String url = repositoryUrl(item);
        String ref = repositoryRef(item);
        Path target = target(kind, id);
        if (Files.exists(target)) throw new IllegalArgumentException("目标目录已存在，请先移除本机副本：" + target.getFileName());
        try { Files.createDirectories(target.getParent()); } catch (IOException e) { throw new IllegalArgumentException("无法创建本机组件目录"); }
        begin(kind, id, "install");
        executor.submit(() -> cloneRepository(kind, id, url, ref, target));
        return snapshot();
    }

    public synchronized Map<String, Object> remove(String kind, String id) {
        if (find(kind, id) == null) throw new IllegalArgumentException("未登记的组件：" + id);
        if (task != null && "running".equals(task.get("status"))) throw new IllegalArgumentException("已有组件任务正在执行");
        Path target = target(kind, id);
        if (!Files.exists(target)) throw new IllegalArgumentException("本机尚未下载该组件");
        begin(kind, id, "remove");
        executor.submit(() -> removeDirectory(target));
        return snapshot();
    }

    private List<Map<String, Object>> entries(String kind, String direction) {
        Map<String, Object> loaded = registry.load();
        if (!Boolean.TRUE.equals(loaded.get("valid"))) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        if ("baseline".equals(kind)) {
            Object raw = ((Map<?, ?>) loaded.get("registry")).get("baselines");
            if (raw instanceof Collection<?> values) for (Object value : values) {
                Map<String, Object> item = map(value);
                if (direction == null || direction.isBlank() || direction.equals(item.get("direction"))) result.add(item);
            }
        } else if ("algorithm".equals(kind)) {
            Object raw = loaded.get("algorithms");
            if (raw instanceof Collection<?> values) for (Object value : values) {
                Map<String, Object> item = map(value);
                if (!"standalone-algorithm".equals(item.get("kind"))) continue;
                if (direction == null || direction.isBlank() || direction.equals(item.get("direction"))) result.add(item);
            }
        } else throw new IllegalArgumentException("组件类型必须是 baseline 或 algorithm");
        return result;
    }

    private Map<String, Object> find(String kind, String id) {
        return entries(kind, null).stream().filter(item -> id.equals(item.get("id"))).findFirst().orElse(null);
    }

    private Map<String, Object> component(String kind, Map<String, Object> item) {
        String id = string(item.get("id"));
        Path path = target(kind, id);
        List<String> blockers = new ArrayList<>();
        boolean downloaded = Files.isDirectory(path) && Files.isDirectory(path.resolve(".git"));
        if (!downloaded) blockers.add("本机尚未下载该组件");
        String manifest = manifestName(item, "algorithm_manifest", "algorithm.yaml");
        if (Files.isDirectory(path) && !Files.isRegularFile(path.resolve(manifest))) blockers.add("缺少 " + manifest);
        if ("algorithm".equals(kind)) {
            String derivedManifest = manifestName(item, "derived_manifest", "derived-from.yaml");
            if (Files.isDirectory(path) && !Files.isRegularFile(path.resolve(derivedManifest))) blockers.add("缺少 " + derivedManifest);
        }
        boolean valid = downloaded && blockers.stream().noneMatch("本机尚未下载该组件"::equals)
                && blockers.stream().noneMatch(blocker -> blocker.startsWith("缺少 "));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id); result.put("name", item.get("name")); result.put("direction", item.get("direction"));
        result.put("kind", kind); result.put("registrationStatus", item.get("status"));
        result.put("code", item.get("code"));
        result.put("baselineId", baselineId(item));
        result.put("localStatus", downloaded ? "downloaded" : "not-downloaded");
        result.put("path", root.relativize(path).toString().replace(FileSystems.getDefault().getSeparator(), "/"));
        result.put("trainingReady", "algorithm".equals(kind) && valid && "published".equals(item.get("status")));
        result.put("blockers", blockers);
        result.put("repository", map(item.get("repository")).get("url"));
        result.put("engine", item.get("engine")); result.put("dataFormat", item.get("data_format"));
        return result;
    }

    private Path target(String kind, String id) {
        String[] parts = id.split("/", 2);
        if (parts.length != 2) throw new IllegalArgumentException("组件 ID 格式无效：" + id);
        Path base = root.resolve("local-components").resolve("baseline".equals(kind) ? "baselines" : "algorithms");
        Path target = base.resolve(parts[0]).resolve(parts[1]).normalize();
        if (!target.startsWith(base)) throw new IllegalArgumentException("组件路径无效");
        return target;
    }

    private void cloneRepository(String kind, String id, String url, String ref, Path target) {
        try {
            stageProgress("正在连接远程仓库", 10);
            stageProgress("正在下载 " + id + "，版本 " + ref, 30);
            runGit(1200, "clone", "--depth", "1", "--branch", ref, url, target.toString());
            stageProgress("仓库对象已下载，正在检查清单", 80);
            Map<String, Object> result = component(kind, find(kind, id));
            String message = Boolean.TRUE.equals(result.get("trainingReady"))
                    ? "组件已下载并可用于训练"
                    : "组件已下载，但平台训练清单尚待补齐";
            finish("succeeded", message);
        } catch (Exception e) { stageProgress(e.getMessage() == null ? "下载失败" : e.getMessage(), 0); finish("failed", "下载失败"); }
    }

    private void removeDirectory(Path target) {
        try {
            stageProgress("正在删除本机组件文件", 50);
            try (var files = Files.walk(target)) { files.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException e) { throw new UncheckedIOException(e); } }); }
            finish("succeeded", "本机组件已移除");
        } catch (Exception e) { stageProgress("移除失败：" + e.getMessage(), 0); finish("failed", "移除失败"); }
    }

    private void begin(String kind, String id, String operation) { task = new LinkedHashMap<>(Map.of("id", UUID.randomUUID().toString(), "componentId", id, "kind", kind, "operation", operation, "status", "running", "progress", 0, "message", "准备中")); logs.clear(); }
    private synchronized void stageProgress(String message, int progress) { if (task != null) { task.put("message", message); task.put("progress", progress); logs.add(message); } }
    private synchronized void finish(String status, String message) { if (task != null) { task.put("status", status); task.put("message", message); task.put("progress", "succeeded".equals(status) ? 100 : task.getOrDefault("progress", 0)); } }
    public synchronized Map<String, Object> snapshot() { if (task == null) return Map.of(); Map<String, Object> copy = new LinkedHashMap<>(task); copy.put("logs", List.copyOf(logs)); return copy; }
    private boolean gitAvailable() { try { return runGit(5, "--version").startsWith("git version"); } catch (Exception e) { return false; } }
    private String runGit(int seconds, String... args) throws Exception { List<String> command = new ArrayList<>(List.of("git", "-c", "credential.interactive=false")); command.addAll(List.of(args)); Process process = new ProcessBuilder(command).directory(root.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start(); process.getOutputStream().close(); if (!process.waitFor(seconds, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalArgumentException("Git 操作超时"); } if (process.exitValue() != 0) throw new IllegalArgumentException("Git 操作失败，请检查网络和仓库权限"); return new String(process.getInputStream().readAllBytes()); }
    private String repositoryUrl(Map<String, Object> item) { String url = string(map(item.get("repository")).get("url")); if (!url.startsWith("https://github.com/") || url.contains("<") || url.contains("@")) throw new IllegalArgumentException("登记仓库地址无效"); return url; }
    private String repositoryRef(Map<String, Object> item) { Map<String, Object> repo = map(item.get("repository")); return Optional.ofNullable(string(repo.get("stable_ref"))).filter(s -> !s.isBlank()).orElseGet(() -> Optional.ofNullable(string(repo.get("default_ref"))).filter(s -> !s.isBlank()).orElse(string(repo.get("ref")))); }
    private String baselineId(Map<String, Object> item) {
        Map<String, Object> derivedFrom = map(item.get("derived_from"));
        Map<String, Object> baseline = map(derivedFrom.get("baseline"));
        return string(baseline.get("id"));
    }
    private String manifestName(Map<String, Object> item, String field, String fallback) {
        String value = string(item.get(field));
        if (value.isBlank() || value.startsWith("/") || value.contains("..")) return fallback;
        return value.replace('\\', '/');
    }
    private static Map<String, Object> map(Object value) { if (!(value instanceof Map<?, ?> raw)) return Map.of(); Map<String, Object> result = new LinkedHashMap<>(); raw.forEach((key, val) -> result.put(String.valueOf(key), val)); return result; }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
