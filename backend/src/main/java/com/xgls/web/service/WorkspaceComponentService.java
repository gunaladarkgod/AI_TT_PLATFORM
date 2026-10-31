package com.xgls.web.service;

import com.xgls.web.utils.WorkspacePathUtil;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Fixed component catalog is part of the platform, including in core-only checkouts. */
@Service
public class WorkspaceComponentService {
    private final Path root;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Map<String, Object> task;
    private final List<String> logs = new ArrayList<>();

    public WorkspaceComponentService() { this(WorkspacePathUtil.workspaceRoot()); }
    WorkspaceComponentService(Path root) { this.root = root.toAbsolutePath().normalize(); }
    private static final Map<String, String> ENGINES = Map.of(
        "mmdet", "engines/mmdet_run", "ultralytics", "engines/yolo_run/ultralytics_runner");
    private static final Map<String, String> DIRECTIONS = Map.of(
        "small-object", "小目标检测", "knowledge-distillation", "知识蒸馏");

    private List<String> paths(String id, String engine) {
        if (engine == null || !ENGINES.containsKey(engine)) throw new IllegalArgumentException("请选择有效的训练引擎");
        if (id == null) throw new IllegalArgumentException("请选择组件");
        if (ENGINES.containsKey(id)) return List.of(ENGINES.get(id));
        if (!DIRECTIONS.containsKey(id)) throw new IllegalArgumentException("未知组件");
        String prefix = "research/" + id + "/";
        return engine.equals("ultralytics")
            ? List.of(prefix + "ultralytics-yolo11n-v1", prefix + "ultralytics-yolov5s-v1", prefix + "ultralytics-yolov5m-v1")
            : List.of(prefix + (id.equals("small-object") ? "mmdet-faster-rcnn-r50-v1" : "mmdet-detr-r50-v1"));
    }

    private List<String> required(String id, String engine) {
        if (id.equals("mmdet")) return List.of(ENGINES.get(id) + "/mmdetection-3.0.0/mmdet/__init__.py",
            ENGINES.get(id) + "/mmdetection-3.0.0/tools/train.py",
            ENGINES.get(id) + "/myfiles/template/rcnn/faster r-cnn.py", ENGINES.get(id) + "/myfiles/template/detr/detr.py");
        if (id.equals("ultralytics")) return List.of(ENGINES.get(id) + "/requirements.txt");
        List<String> files = new ArrayList<>();
        files.add("research/" + id + "/direction.yaml");
        paths(id, engine).forEach(p -> files.add(p + "/algorithm.yaml"));
        return files;
    }

    public Map<String, Object> component(String id, String engine) {
        List<String> selected = paths(id, engine);
        List<String> missing = required(id, engine).stream().filter(p -> !Files.isRegularFile(root.resolve(p))).toList();
        boolean any = selected.stream().anyMatch(p -> Files.exists(root.resolve(p)));
        String status = missing.isEmpty() ? "ready" : any ? "partial" : "missing";
        return Map.of("id", id, "kind", ENGINES.containsKey(id) ? "engine" : "direction",
            "name", DIRECTIONS.getOrDefault(id, id.equals("mmdet") ? "MMDetection" : "Ultralytics"),
            "status", status, "relativePath", String.join(", ", selected), "engine", engine,
            "message", missing.isEmpty() ? "组件关键文件齐全；Python 依赖与实际训练能力需另行检查。研究草稿不代表算法已实现。"
                : "缺少：" + String.join("、", missing));
    }

    public synchronized Map<String, Object> list(String engine) {
        paths(engine, engine);
        List<Map<String, Object>> items = new ArrayList<>();
        for (String id : List.of("mmdet", "ultralytics", "small-object", "knowledge-distillation")) items.add(component(id, engine));
        String reason = "";
        try { checkWorkspace(); } catch (Exception e) { reason = e.getMessage(); }
        return Map.of("components", items, "downloadSupported", reason.isEmpty(), "message", reason, "task", snapshot());
    }

    public synchronized Map<String, Object> start(String id, String engine) {
        if (id != null && ENGINES.containsKey(id)) engine = id;
        List<String> selected = new ArrayList<>(paths(id, engine));
        if (DIRECTIONS.containsKey(id)) selected.add(0, ENGINES.get(engine));
        begin(id, engine, "download");
        String selectedEngine = engine;
        executor.submit(() -> download(id, selectedEngine, selected));
        return snapshot();
    }

    public synchronized Map<String, Object> remove(String id, String engine) {
        if (id != null && ENGINES.containsKey(id)) engine = id;
        List<String> selected = paths(id, engine);
        begin(id, engine, "remove");
        String selectedEngine = engine;
        executor.submit(() -> removeFiles(id, selectedEngine, selected));
        return snapshot();
    }

    private void begin(String id, String engine, String operation) {
        if (task != null && "running".equals(task.get("status"))) throw new IllegalArgumentException("已有组件任务正在执行，请等待完成");
        checkWorkspace();
        task = new LinkedHashMap<>(Map.of("id", UUID.randomUUID().toString(), "componentId", id,
            "engine", engine, "operation", operation, "status", "running", "message", "正在检查工作区"));
        logs.clear();
    }

    private void checkClean(List<String> selected, boolean includeIgnored) {
        List<String> status = new ArrayList<>(List.of("status", "--porcelain", "--untracked-files=all", "--"));
        status.addAll(selected);
        if (!git(30, status.toArray(String[]::new)).isBlank()) throw new IllegalArgumentException("目标组件含本地修改或未跟踪文件，请先保存并处理后重试");
        if (includeIgnored) {
            List<String> ignored = new ArrayList<>(List.of("ls-files", "--others", "--ignored", "--exclude-standard", "--"));
            ignored.addAll(selected);
            if (!git(30, ignored.toArray(String[]::new)).isBlank()) throw new IllegalArgumentException("组件目录内含环境、缓存或运行文件，请先移走这些文件再删除；平台不会清除个人文件");
        }
    }

    private void removeFiles(String id, String engine, List<String> selected) {
        try {
            checkClean(selected, true);
            if (ENGINES.containsKey(id)) {
                for (String direction : DIRECTIONS.keySet()) {
                    if (paths(direction, engine).stream().anyMatch(p -> Files.exists(root.resolve(p))))
                        throw new IllegalArgumentException("仍有研究方向依赖该引擎，请先删除该引擎对应的研究方向组件");
                }
            }
            List<String> current = git(15, "sparse-checkout", "list").lines().filter(s -> !s.isBlank()).toList();
            for (String existing : current) {
                if (!existing.matches("[A-Za-z0-9_./-]+")) throw new IllegalArgumentException("当前 sparse 选择含特殊路径，请通过 Git 手动调整，以保留原始选择");
                for (String target : selected) {
                    if (existing.equals(".") || target.startsWith(existing + "/"))
                        throw new IllegalArgumentException("组件被更大的 sparse 目录选择包含，请先通过 Git 将选择细分后再删除");
                }
            }
            List<String> remaining = current.stream().filter(p -> selected.stream().noneMatch(t -> p.equals(t) || p.startsWith(t + "/"))).toList();
            // Recheck immediately before changing the working tree. Git itself also protects tracked edits.
            checkClean(selected, true);
            stage("正在移除组件源码，保留其他组件和平台文件");
            List<String> args = new ArrayList<>(List.of("sparse-checkout", "set", "--cone", "--"));
            args.addAll(remaining);
            git(60, args.toArray(String[]::new));
            if (selected.stream().anyMatch(p -> Files.exists(root.resolve(p))))
                throw new IllegalArgumentException("组件仍有残留文件或被其他选择包含，请检查目录后重试；平台未强制删除文件");
            stage("组件已删除，可重新下载；独立 Python 环境、数据集和训练结果保留");
            synchronized (this) { task.put("status", "succeeded"); }
        } catch (Exception e) {
            stage(e.getMessage() == null ? "删除失败" : e.getMessage());
            synchronized (this) { task.put("status", "failed"); }
        }
    }

    public synchronized Map<String, Object> snapshot() {
        if (task == null) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>(task);
        copy.put("logs", List.copyOf(logs));
        return copy;
    }
    private synchronized void stage(String message) { task.put("message", message); logs.add(message); }
    private void checkWorkspace() {
        if (!git(15, "rev-parse", "--show-toplevel").trim().replace('\\', '/').equals(root.toString().replace('\\', '/')))
            throw new IllegalArgumentException("后端工作区必须是 Git 仓库根目录；ZIP 下载不支持补充下载");
        if (!"true".equals(git(15, "config", "--bool", "core.sparseCheckout").trim()) ||
            !"true".equals(git(15, "config", "--bool", "core.sparseCheckoutCone").trim()))
            throw new IllegalArgumentException("当前不是 cone 模式的选择性克隆工作区；完整克隆无需下载，请检查缺失文件或分支");
    }
    private void download(String id, String engine, List<String> selected) {
        try {
            checkClean(selected, false);
            stage("正在补充组件文件，已有选择将保留；首次下载可能需要较长时间");
            List<String> args = new ArrayList<>(List.of("sparse-checkout", "add", "--"));
            args.addAll(selected);
            git(600, args.toArray(String[]::new));
            stage("正在验证下载后的文件");
            if (!component(id, engine).get("status").equals("ready") ||
                !component(engine, engine).get("status").equals("ready"))
                throw new IllegalArgumentException("当前分支缺少组件关键文件，请检查分支内容；已有下载文件会保留");
            stage("下载完成，组件文件齐全；请继续检查训练 Python 环境");
            synchronized (this) { task.put("status", "succeeded"); }
        } catch (Exception e) {
            stage(e.getMessage() == null ? "下载失败" : e.getMessage());
            synchronized (this) { task.put("status", "failed"); }
        }
    }

    // No shell, client paths, credentials, checkout/reset or branch changes. Never return raw Git stderr.
    private String git(int seconds, String... args) {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("ai-tt-component-", ".log");
            List<String> command = new ArrayList<>(List.of("git", "-c", "credential.interactive=false"));
            command.addAll(List.of(args));
            ProcessBuilder pb = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.environment().put("GCM_INTERACTIVE", "Never");
            pb.environment().put("GIT_SSH_COMMAND", "ssh -oBatchMode=yes -oConnectTimeout=15");
            process = pb.start();
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) throw new IllegalArgumentException("Git 操作超时，请检查网络与仓库访问权限后重试");
            if (process.exitValue() != 0) throw new IllegalArgumentException("Git 操作失败，请检查 Git 安装、仓库权限、凭据、网络或工作区状态；平台不会弹出认证窗口");
            return Files.readString(output);
        } catch (java.io.IOException e) { throw new IllegalArgumentException("无法运行 Git，请检查安装与工作区访问权限");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalArgumentException("下载已中断，可刷新检查并重试");
        } finally {
            if (process != null && process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
            if (output != null) try { Files.deleteIfExists(output); } catch (java.io.IOException ignored) { }
        }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
