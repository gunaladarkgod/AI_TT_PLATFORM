package com.xgls.web.service;

import com.xgls.web.utils.WorkspacePathUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Installs only C repositories explicitly registered under research-registry.
 * It is deliberately separate from WorkspaceComponentService: that legacy service
 * changes sparse-checkout paths inside the platform repository, whereas this one
 * clones an independent repository into ignored local-components/.
 */
@Service
public class AlgorithmComponentService {
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*/[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SAFE_RELATIVE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]*");
    private static final long MAX_YAML_BYTES = 512 * 1024L;

    private final Path root;
    private final Path registryRoot;
    private final Path algorithmRoot;
    private final Path stagingRoot;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Map<String, Object> task;
    private final List<String> logs = new ArrayList<>();

    public AlgorithmComponentService() {
        this(WorkspacePathUtil.workspaceRoot());
    }

    AlgorithmComponentService(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.registryRoot = this.root.resolve("research-registry").normalize();
        this.algorithmRoot = this.root.resolve("local-components").resolve("algorithms").normalize();
        this.stagingRoot = this.root.resolve("local-components").resolve(".staging").normalize();
    }

    /** Read-only data for the training page and the later registry management page. */
    public synchronized Map<String, Object> list(String direction) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (AlgorithmRecord record : records(errors)) {
            if (direction == null || direction.isBlank() || direction.equals(record.direction)) {
                items.add(inspect(record));
            }
        }
        return Map.of("components", items, "registryErrors", errors, "task", snapshot());
    }

    /**
     * A server-side training integration can call this after resolving a task.
     * It returns metadata only; it never exposes a client-supplied command or
     * starts a process. Generic Runner execution is intentionally a later step.
     */
    public synchronized Map<String, Object> trainingProfile(String id) {
        AlgorithmRecord record = record(id);
        Map<String, Object> component = inspect(record);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", record.id);
        profile.put("name", record.name);
        profile.put("direction", record.direction);
        profile.put("engine", Map.of("id", record.engineId, "version", record.engineVersion));
        profile.put("dataFormat", record.dataFormat);
        profile.put("localStatus", component.get("localStatus"));
        profile.put("trainingReady", component.get("trainingReady"));
        profile.put("blockers", component.get("blockers"));
        profile.put("manifest", record.manifest);
        profile.put("sourceCommit", component.get("sourceCommit"));
        profile.put("runnerIntegration", "not-connected");
        profile.put("message", "当前接口只提供受控组件的训练选择与预检信息；Runner 调用必须在 C 清单定义入口并完成独立接入后实现。");
        return profile;
    }

    public synchronized Map<String, Object> startInstall(String id) {
        AlgorithmRecord record = record(id);
        if (!record.installable()) throw new IllegalArgumentException("该算法当前状态不允许下载：" + record.status);
        Map<String, Object> state = inspect(record);
        if (!"not-downloaded".equals(state.get("localStatus"))) {
            throw new IllegalArgumentException("本机组件目录已存在，请先处理当前状态；平台不会覆盖已有算法源码");
        }
        begin(record, "install");
        executor.submit(() -> install(record));
        return snapshot();
    }

    public synchronized Map<String, Object> startRemove(String id) {
        AlgorithmRecord record = record(id);
        Map<String, Object> state = inspect(record);
        if ("not-downloaded".equals(state.get("localStatus"))) {
            throw new IllegalArgumentException("该算法尚未下载到本机");
        }
        begin(record, "remove");
        executor.submit(() -> remove(record));
        return snapshot();
    }

    public synchronized Map<String, Object> snapshot() {
        if (task == null) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>(task);
        copy.put("logs", List.copyOf(logs));
        return copy;
    }

    private void install(AlgorithmRecord record) {
        Path target = target(record);
        Path staging = stagingRoot.resolve(safeFileName(record.id) + "-" + UUID.randomUUID()).normalize();
        try {
            Files.createDirectories(stagingRoot);
            if (Files.exists(target)) throw new IllegalArgumentException("本机组件目录已存在，拒绝覆盖");
            stage("正在下载已登记算法源码；不会读取客户端提供的 URL 或命令");
            git(root, 600, "clone", "--depth", "1", "--single-branch", "--branch", record.ref(), record.repository, staging.toString());
            stage("源码已下载，正在校验算法清单");
            Files.createDirectories(target.getParent());
            moveDirectory(staging, target);
            Map<String, Object> inspection = inspect(record);
            if (!"downloaded".equals(inspection.get("localStatus"))) {
                throw new IllegalArgumentException("源码已保留，但算法清单校验未通过：" + String.join("；", stringList(inspection.get("blockers"))));
            }
            stage("下载并校验完成；该组件可出现在训练页的已下载算法列表中");
            succeed();
        } catch (Exception e) {
            if (Files.exists(staging) && !Files.exists(target)) {
                try { Files.createDirectories(target.getParent()); moveDirectory(staging, target); }
                catch (Exception ignored) { }
            }
            fail(message(e));
        } finally {
            deleteIfEmpty(staging);
        }
    }

    private void remove(AlgorithmRecord record) {
        try {
            Path target = target(record);
            ensureGitClean(target);
            stage("正在移除本机算法副本；不会影响远程仓库、基线仓库或训练结果");
            deleteTree(target);
            stage("已移除本机算法副本");
            succeed();
        } catch (Exception e) {
            fail(message(e));
        }
    }

    private synchronized void begin(AlgorithmRecord record, String operation) {
        if (task != null && "running".equals(task.get("status"))) {
            throw new IllegalArgumentException("已有算法组件任务正在执行，请等待完成");
        }
        task = new LinkedHashMap<>();
        task.put("id", UUID.randomUUID().toString());
        task.put("algorithmId", record.id);
        task.put("operation", operation);
        task.put("status", "running");
        task.put("message", "正在准备操作");
        task.put("startedAt", Instant.now().toString());
        logs.clear();
    }

    private synchronized void stage(String message) {
        task.put("message", message);
        logs.add(message);
    }

    private synchronized void succeed() {
        task.put("status", "succeeded");
        task.put("finishedAt", Instant.now().toString());
    }

    private synchronized void fail(String message) {
        stage(message == null || message.isBlank() ? "操作失败" : message);
        task.put("status", "failed");
        task.put("finishedAt", Instant.now().toString());
    }

    private AlgorithmRecord record(String id) {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("算法登记 ID 非法");
        List<Map<String, Object>> errors = new ArrayList<>();
        for (AlgorithmRecord record : records(errors)) if (record.id.equals(id)) return record;
        if (!errors.isEmpty()) throw new IllegalArgumentException("算法登记不可用：" + errors.get(0).get("message"));
        throw new IllegalArgumentException("未登记的算法组件：" + id);
    }

    private List<AlgorithmRecord> records(List<Map<String, Object>> errors) {
        Path directory = registryRoot.resolve("algorithms").normalize();
        if (!Files.isDirectory(directory)) {
            errors.add(Map.of("file", "research-registry/algorithms", "message", "算法登记目录不存在"));
            return List.of();
        }
        List<AlgorithmRecord> records = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".yaml")).sorted().toList()) {
                try {
                    Map<String, Object> raw = yaml(path);
                    if (!"external-git".equals(text(map(raw.get("source")).get("type")))) continue;
                    AlgorithmRecord record = parseRecord(raw, path);
                    if (!ids.add(record.id)) throw new IllegalArgumentException("算法登记 ID 重复：" + record.id);
                    records.add(record);
                } catch (IllegalArgumentException e) {
                    errors.add(Map.of("file", root.relativize(path).toString().replace('\\', '/'), "message", message(e)));
                }
            }
        } catch (IOException e) {
            errors.add(Map.of("file", "research-registry/algorithms", "message", "无法读取算法登记目录"));
        }
        return records.stream().sorted(Comparator.comparing(record -> record.id)).toList();
    }

    private AlgorithmRecord parseRecord(Map<String, Object> raw, Path path) {
        String id = required(raw, "id", path);
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("算法 ID 非法：" + id);
        String kind = required(raw, "kind", path);
        if (!"standalone-algorithm".equals(kind)) throw new IllegalArgumentException("外部算法必须使用 kind: standalone-algorithm");
        String status = required(raw, "status", path);
        if (!Set.of("draft", "review", "published", "deprecated", "archived").contains(status)) throw new IllegalArgumentException("未知登记状态：" + status);
        Map<String, Object> source = map(raw.get("source"));
        String repository = required(source, "repository", path);
        if (!(repository.startsWith("https://") || repository.startsWith("git@")) || repository.contains("\n") || repository.contains("\r")) {
            throw new IllegalArgumentException("仓库地址仅允许 HTTPS 或 SSH Git 地址");
        }
        String ref = text(source.get("stable_ref"));
        if (ref.isBlank()) ref = required(source, "default_ref", path);
        if (!ref.matches("[A-Za-z0-9._/@-]+")) throw new IllegalArgumentException("仓库版本标识非法");
        String manifest = required(source, "manifest", path);
        if (!SAFE_RELATIVE.matcher(manifest).matches() || manifest.contains("..")) throw new IllegalArgumentException("算法清单相对路径非法");
        String derivedManifest = required(source, "derived_manifest", path);
        if (!SAFE_RELATIVE.matcher(derivedManifest).matches() || derivedManifest.contains("..")) throw new IllegalArgumentException("基线来源清单相对路径非法");
        Map<String, Object> engine = map(raw.get("engine"));
        String engineId = required(engine, "id", path);
        String engineVersion = required(engine, "version", path);
        String direction = required(raw, "direction", path);
        String dataFormat = required(raw, "data_format", path);
        if (!SEGMENT.matcher(direction).matches() || !directionExists(direction)) {
            throw new IllegalArgumentException("算法引用未知或非法研究方向：" + direction);
        }
        if (!Set.of("mmdet", "ultralytics", "custom", "paper").contains(engineId)) {
            throw new IllegalArgumentException("算法引擎不受支持：" + engineId);
        }
        if (!Set.of("coco", "yolo", "custom").contains(dataFormat)) {
            throw new IllegalArgumentException("算法数据格式不受支持：" + dataFormat);
        }
        Map<String, Object> derivedFrom = map(raw.get("derived_from"));
        Map<String, Object> baseline = map(derivedFrom.get("baseline"));
        if (baseline.isEmpty() || text(baseline.get("repository")).isBlank() || text(baseline.get("ref")).isBlank()
                || text(baseline.get("commit")).isBlank()) {
            throw new IllegalArgumentException("独立算法必须声明 derived_from.baseline 的仓库、版本和提交");
        }
        return new AlgorithmRecord(id, required(raw, "name", path), direction, status, engineId, engineVersion,
                dataFormat, repository, ref, manifest, derivedManifest, derivedFrom, text(raw.get("description")));
    }

    private boolean directionExists(String direction) {
        Path path = registryRoot.resolve("directions").resolve(direction + ".yaml").normalize();
        if (!path.startsWith(registryRoot) || !Files.isRegularFile(path)) return false;
        try { return direction.equals(text(yaml(path).get("id"))); }
        catch (IllegalArgumentException e) { return false; }
    }

    private Map<String, Object> inspect(AlgorithmRecord record) {
        Path target = target(record);
        List<String> blockers = new ArrayList<>();
        String sourceCommit = null;
        String localStatus;
        if (!Files.exists(target)) {
            localStatus = "not-downloaded";
            blockers.add("本机尚未下载该算法组件");
        } else if (!Files.isDirectory(target.resolve(".git"))) {
            localStatus = "invalid";
            blockers.add("本机组件不是 Git 仓库，不能确认来源和版本");
        } else {
            try { sourceCommit = git(target, 20, "rev-parse", "HEAD").trim(); }
            catch (IllegalArgumentException e) { blockers.add("无法读取本机算法提交版本"); }
            Path manifest = target.resolve(record.manifest).normalize();
            if (!manifest.startsWith(target) || !Files.isRegularFile(manifest)) {
                blockers.add("缺少算法清单：" + record.manifest);
            } else {
                try { validateManifest(record, yaml(manifest), blockers); }
                catch (IllegalArgumentException e) { blockers.add(message(e)); }
            }
            Path derivedManifest = target.resolve(record.derivedManifest).normalize();
            if (!derivedManifest.startsWith(target) || !Files.isRegularFile(derivedManifest)) {
                blockers.add("缺少基线来源清单：" + record.derivedManifest);
            } else {
                try { validateDerivedFrom(record, yaml(derivedManifest), blockers); }
                catch (IllegalArgumentException e) { blockers.add(message(e)); }
            }
            localStatus = blockers.isEmpty() ? "downloaded" : "invalid";
        }
        boolean trainingReady = "downloaded".equals(localStatus) && record.status.equals("published");
        if ("downloaded".equals(localStatus) && !trainingReady) blockers.add("登记状态为 " + record.status + "，尚未发布为可训练算法");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", record.id);
        out.put("name", record.name);
        out.put("kind", "standalone-algorithm");
        out.put("direction", record.direction);
        out.put("registrationStatus", record.status);
        out.put("engine", Map.of("id", record.engineId, "version", record.engineVersion));
        out.put("dataFormat", record.dataFormat);
        out.put("localStatus", localStatus);
        out.put("trainingReady", trainingReady);
        out.put("blockers", blockers);
        out.put("sourceCommit", sourceCommit);
        out.put("derivedFrom", record.derivedFrom);
        out.put("installAllowed", record.installable() && "not-downloaded".equals(localStatus));
        out.put("removeAllowed", !"not-downloaded".equals(localStatus));
        return out;
    }

    private void validateManifest(AlgorithmRecord record, Map<String, Object> manifest, List<String> blockers) {
        if (!record.id.equals(text(manifest.get("id")))) blockers.add("算法清单 ID 与平台登记不一致");
        Map<String, Object> engine = map(manifest.get("engine"));
        if (!record.engineId.equals(text(engine.get("id")))) blockers.add("算法清单引擎与平台登记不一致");
        if (!record.dataFormat.equals(text(manifest.get("data_format")))) blockers.add("算法清单数据格式与平台登记不一致");
        Map<String, Object> entry = map(manifest.get("entry"));
        String trainEntry = text(entry.get("train"));
        if (trainEntry.isBlank() || !SAFE_RELATIVE.matcher(trainEntry).matches() || trainEntry.contains("..")) {
            blockers.add("算法清单缺少安全的 entry.train 定义");
        } else if (!Files.isRegularFile(target(record).resolve(trainEntry).normalize())) {
            blockers.add("算法训练入口不存在：" + trainEntry);
        }
    }

    private void validateDerivedFrom(AlgorithmRecord record, Map<String, Object> derived, List<String> blockers) {
        Map<String, Object> registered = map(record.derivedFrom.get("baseline"));
        Map<String, Object> actual = map(derived.get("baseline"));
        for (String key : List.of("repository", "ref", "commit")) {
            if (!text(registered.get(key)).equals(text(actual.get(key)))) {
                blockers.add("基线来源清单与平台登记的 " + key + " 不一致");
            }
        }
    }

    private Path target(AlgorithmRecord record) {
        String[] segments = record.id.split("/", -1);
        Path result = algorithmRoot.resolve(segments[0]).resolve(segments[1]).normalize();
        if (!result.startsWith(algorithmRoot)) throw new IllegalArgumentException("算法本机目录非法");
        return result;
    }

    private void ensureGitClean(Path target) {
        if (!Files.isDirectory(target.resolve(".git"))) throw new IllegalArgumentException("组件目录不是 Git 仓库，平台不会删除它");
        String status = git(target, 30, "status", "--porcelain", "--untracked-files=all");
        if (!status.isBlank()) throw new IllegalArgumentException("算法组件含已修改或未跟踪文件，请先提交、备份或手动处理后再移除");
    }

    private Map<String, Object> yaml(Path path) {
        try {
            if (Files.size(path) > MAX_YAML_BYTES) throw new IllegalArgumentException("YAML 文件过大");
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            try (InputStream stream = Files.newInputStream(path)) {
                Object raw = new Yaml(new SafeConstructor(options)).load(stream);
                if (!(raw instanceof Map<?, ?>)) throw new IllegalArgumentException("YAML 根节点必须是对象");
                return map(raw);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取 YAML 文件：" + path.getFileName());
        }
    }

    private String git(Path directory, int seconds, String... args) {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("ai-tt-algorithm-component-", ".log");
            List<String> command = new ArrayList<>(List.of("git", "-c", "credential.interactive=false"));
            command.addAll(List.of(args));
            ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(output.toFile());
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            builder.environment().put("GCM_INTERACTIVE", "Never");
            builder.environment().put("GIT_SSH_COMMAND", "ssh -oBatchMode=yes -oConnectTimeout=15");
            process = builder.start();
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) throw new IllegalArgumentException("Git 操作超时，请检查网络、仓库权限和凭据");
            if (process.exitValue() != 0) throw new IllegalArgumentException("Git 操作失败，请检查 Git、网络、仓库权限或系统凭据；平台不会弹出认证窗口");
            return Files.readString(output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法运行 Git，请检查 Git 安装和工作区访问权限");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("算法组件操作已中断");
        } finally {
            if (process != null && process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
            if (output != null) try { Files.deleteIfExists(output); } catch (IOException ignored) { }
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(source, target); }
    }

    private void deleteIfEmpty(Path directory) {
        try (var paths = Files.isDirectory(directory) ? Files.list(directory) : null) {
            if (paths != null && paths.findAny().isEmpty()) Files.delete(directory);
        }
        catch (IOException ignored) { }
    }

    private void deleteTree(Path target) throws IOException {
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private static String required(Map<String, Object> values, String name, Path path) {
        String value = text(values.get(name));
        if (value.isBlank()) throw new IllegalArgumentException("登记缺少字段 " + name + "：" + path.getFileName());
        return value;
    }

    private static Map<String, Object> map(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> values) for (Map.Entry<?, ?> entry : values.entrySet()) out.put(String.valueOf(entry.getKey()), entry.getValue());
        return out;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String message(Exception exception) { return exception.getMessage() == null ? "操作失败" : exception.getMessage(); }
    private static List<String> stringList(Object values) { return values instanceof Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of(); }
    private static String safeFileName(String id) { return id.replace('/', '-'); }
    @PreDestroy public void close() { executor.shutdownNow(); }

    private record AlgorithmRecord(String id, String name, String direction, String status, String engineId,
                                   String engineVersion, String dataFormat, String repository, String ref,
                                   String manifest, String derivedManifest, Map<String, Object> derivedFrom, String description) {
        boolean installable() { return !"deprecated".equals(status) && !"archived".equals(status); }
    }
}
