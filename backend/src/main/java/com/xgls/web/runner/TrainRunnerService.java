package com.xgls.web.runner;

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xgls.web.utils.WorkspacePathUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class TrainRunnerService {

    private static final String RUNNER_IMPORT_CHECK =
            "import fastapi,uvicorn,mmengine; from mmengine.config import Config; "
                    + "print('fastapi='+fastapi.__version__); print('uvicorn='+uvicorn.__version__); "
                    + "print('mmengine='+mmengine.__version__)";

    @Value("${sys.runner.train-url:http://127.0.0.1:8009/api/runner/train}")
    private String runnerTrainUrl;

    @Value("${sys.runner.launch-script:mmdet_run/mmdet_runner_srv/start_runner.sh}")
    private String runnerLaunchScript;

    @Value("${sys.runner.auto-start-log:mmdet_run/logs/runner-autostart.log}")
    private String runnerAutoStartLog;

    private final AtomicReference<Process> manualRunnerProcessRef = new AtomicReference<>();

    /** 检查独立 Runner Python 及其最小依赖，不启动 Runner。 */
    public Map<String, Object> checkRunnerDependencies() {
        Map<String, Object> out = new LinkedHashMap<>();
        Path script = resolveLaunchScript();
        Path python = resolveRunnerPython(script);
        Path requirements = script.getParent().resolve("requirements.txt").normalize();
        out.put("python", python.toString());
        out.put("requirements", requirements.toString());
        out.put("pythonExists", Files.isRegularFile(python));
        out.put("requirementsExists", Files.isRegularFile(requirements));
        if (!Files.isRegularFile(python)) {
            out.put("ok", false);
            out.put("message", "Runner Python 不存在");
            return out;
        }
        try {
            Process p = new ProcessBuilder(python.toString(), "-c", RUNNER_IMPORT_CHECK)
                    .redirectErrorStream(true).start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int code = p.waitFor();
            out.put("ok", code == 0);
            out.put("exitCode", code);
            out.put("detail", text);
            out.put("message", code == 0 ? "Runner 依赖已安装" : "Runner 依赖缺失或损坏");
        } catch (Exception e) {
            out.put("ok", false);
            out.put("message", "依赖检测失败");
            out.put("detail", e.getMessage());
        }
        return out;
    }

    /** 安装 requirements.txt 后重新检测。该操作仅由前端用户明确触发。 */
    public synchronized Map<String, Object> installRunnerDependencies() {
        Map<String, Object> before = checkRunnerDependencies();
        if (Boolean.TRUE.equals(before.get("ok"))) return before;
        Path script = resolveLaunchScript();
        Path python = resolveRunnerPython(script);
        Path requirements = script.getParent().resolve("requirements.txt").normalize();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("before", before);
        if (!Files.isRegularFile(python) || !Files.isRegularFile(requirements)) {
            out.put("ok", false);
            out.put("message", "Runner Python 或 requirements.txt 不存在");
            return out;
        }
        try {
            Process p = new ProcessBuilder(python.toString(), "-m", "pip", "install", "-r", requirements.toString())
                    .redirectErrorStream(true).start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            out.put("installExitCode", code);
            out.put("installLog", StrUtil.maxLength(text, 8000));
            Map<String, Object> after = checkRunnerDependencies();
            out.put("after", after);
            out.put("ok", code == 0 && Boolean.TRUE.equals(after.get("ok")));
            out.put("message", Boolean.TRUE.equals(out.get("ok")) ? "Runner 依赖安装完成" : "Runner 依赖安装失败");
        } catch (Exception e) {
            out.put("ok", false);
            out.put("message", "执行 pip 安装失败");
            out.put("detail", e.getMessage());
        }
        return out;
    }

    private Path resolveRunnerPython(Path script) {
        Path dir = script.getParent();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path conda = dir.resolve(windows ? ".conda_runner/python.exe" : ".conda_runner/bin/python");
        if (Files.isRegularFile(conda)) return conda.toAbsolutePath().normalize();
        Path venv = dir.resolve(windows ? ".venv/Scripts/python.exe" : ".venv/bin/python");
        return venv.toAbsolutePath().normalize();
    }

    /** 探测 Runner HTTP 服务是否可用（GET /health，与 train-url 同主机端口）。 */
    public Map<String, Object> probeHealth() {
        Map<String, Object> out = new LinkedHashMap<>();
        URI health = runnerHealthUri();
        out.put("healthUrl", health.toString());
        long t0 = System.nanoTime();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest req = HttpRequest.newBuilder(health).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            out.put("latencyMs", ms);
            out.put("httpStatus", resp.statusCode());
            boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 300;
            out.put("ok", ok);
            String body = resp.body();
            if (StrUtil.isNotBlank(body)) {
                out.put("bodyPreview", StrUtil.maxLength(body, 200));
            }
        } catch (Exception e) {
            out.put("ok", false);
            out.put("latencyMs", (System.nanoTime() - t0) / 1_000_000L);
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return out;
    }

    /** 手动启动 Runner，并返回可展示给前端的诊断信息。 */
    public synchronized Map<String, Object> startRunnerManually() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> before = probeHealth();
        out.put("beforeHealth", before);
        if (Boolean.TRUE.equals(before.get("ok"))) {
            out.put("ok", true);
            out.put("started", false);
            out.put("message", "Runner 已经在运行，无需重复启动");
            return out;
        }

        Path script = resolveLaunchScript();
        Path logFile = resolveRunnerLogPath(script);
        out.put("script", script.toString());
        out.put("log", logFile.toString());
        if (!Files.isRegularFile(script)) {
            out.put("ok", false);
            out.put("started", false);
            out.put("message", "Runner 启动脚本不存在");
            out.put("error", "script not found: " + script);
            return out;
        }

        try {
            Files.createDirectories(logFile.getParent());
        } catch (Exception e) {
            out.put("logDirWarning", e.getMessage());
        }

        try {
            ProcessBuilder pb = buildRunnerProcessBuilder(script);
            pb.directory(script.getParent().toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            pb.environment().putIfAbsent("PYTHONNOUSERSITE", "1");
            pb.environment().putIfAbsent("PYTHONUTF8", "1");
            applyWorkspaceRunnerEnv(pb);
            Process p = pb.start();
            manualRunnerProcessRef.set(p);
            out.put("started", true);
            out.put("pid", p.pid());

            long deadline = System.currentTimeMillis() + 25_000L;
            Map<String, Object> lastHealth = before;
            while (System.currentTimeMillis() < deadline) {
                if (!p.isAlive()) {
                    out.put("ok", false);
                    out.put("message", "Runner 进程启动后已退出");
                    out.put("exitCode", p.exitValue());
                    out.put("logTail", tailLog(logFile, 3000));
                    return out;
                }
                lastHealth = probeHealth();
                if (Boolean.TRUE.equals(lastHealth.get("ok"))) {
                    out.put("ok", true);
                    out.put("message", "Runner 启动成功");
                    out.put("health", lastHealth);
                    out.put("logTail", tailLog(logFile, 1200));
                    return out;
                }
                Thread.sleep(500L);
            }

            out.put("ok", false);
            out.put("message", "Runner 已启动进程，但健康检查超时未通过");
            out.put("health", lastHealth);
            out.put("logTail", tailLog(logFile, 3000));
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.put("ok", false);
            out.put("message", "Runner 启动等待被中断");
            out.put("error", e.getMessage());
            out.put("logTail", tailLog(logFile, 3000));
            return out;
        } catch (Exception e) {
            out.put("ok", false);
            out.put("message", "Runner 启动失败");
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            out.put("logTail", tailLog(logFile, 3000));
            return out;
        }
    }
    /** 由 Runner 使用 MMEngine Config 读取模板、写入参数并生成单一 config.py。 */
    public JSONObject generateConfig(JSONObject payload) {
        return postConfigJson("/api/config/generate", payload, Duration.ofMinutes(3));
    }

    /** 获取模板目录中实际存在/缺失的网络模板。 */
    public JSONObject getConfigTemplates() {
        return getConfigJson("/api/config/templates", Duration.ofSeconds(15));
    }

    /** 读取某个模板文件中的默认训练参数，用于创建任务弹窗回填前端。 */
    public JSONObject getTemplateDefaults(String templateName) {
        String query = "template=" + URLEncoder.encode(templateName, StandardCharsets.UTF_8);
        return getConfigJson("/api/config/template/defaults?" + query, Duration.ofSeconds(30));
    }

    /** 读取已生成的单文件配置摘要；includeText=true 时同时返回源码。 */
    public JSONObject readConfig(String runId, boolean includeText) {
        String query = "runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8)
                + "&includeText=" + includeText;
        return getConfigJson("/api/config/read?" + query, Duration.ofSeconds(30));
    }

    private JSONObject postConfigJson(String path, JSONObject payload, Duration timeout) {
        try {
            String json = payload == null ? "{}" : payload.toString();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(runnerUri(path))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return requireOkConfigResponse(response);
        } catch (Exception e) {
            throw new IllegalStateException("调用 MMDet 配置服务失败: " + e.getMessage(), e);
        }
    }

    private JSONObject getConfigJson(String pathAndQuery, Duration timeout) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(runnerUri(pathAndQuery))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return requireOkConfigResponse(response);
        } catch (Exception e) {
            throw new IllegalStateException("调用 MMDet 配置服务失败: " + e.getMessage(), e);
        }
    }

    private JSONObject requireOkConfigResponse(HttpResponse<String> response) {
        JSONObject body = JSONUtil.parseObj(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || !body.getBool("ok", false)) {
            String detail = body.getStr("error", body.getStr("message", "配置服务返回异常"));
            if ("配置服务返回异常".equals(detail) && response.body() != null && !response.body().isBlank()) {
                detail = "HTTP " + response.statusCode() + ": " + response.body();
            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                detail = "HTTP " + response.statusCode() + ": " + detail;
            }
            throw new IllegalStateException(detail);
        }
        return body;
    }

    private URI runnerUri(String pathAndQuery) throws Exception {
        URI train = URI.create(runnerTrainUrl);
        int port = train.getPort();
        if (port < 0) port = "https".equalsIgnoreCase(train.getScheme()) ? 443 : 80;
        String path = pathAndQuery;
        String query = null;
        int q = pathAndQuery.indexOf('?');
        if (q >= 0) {
            path = pathAndQuery.substring(0, q);
            query = pathAndQuery.substring(q + 1);
        }
        return new URI(train.getScheme(), null, train.getHost(), port, path, query, null);
    }

    /** 获取指定 runId 最近一次运行产生的训练日志。 */
    public JSONObject getLatestTrainLog(String runId, int tailLines) {
        try {
            URI train = URI.create(runnerTrainUrl);
            int port = train.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(train.getScheme()) ? 443 : 80;
            }
            String query = "runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8)
                    + "&tailLines=" + tailLines;
            String endpoint = new URI(train.getScheme(), null, train.getHost(), port,
                    "/api/runner/log/latest", null, null).toString();
            URI uri = URI.create(endpoint + "?" + query);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JSONObject body = JSONUtil.parseObj(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || !body.getBool("ok", false)) {
                throw new IllegalStateException(body.getStr("message", "训练日志不存在"));
            }
            return body;
        } catch (Exception e) {
            throw new IllegalStateException("读取最新训练日志失败: " + e.getMessage(), e);
        }
    }

    /** 删除某条训练结果对应的 Runner 本地产物目录。 */
    public boolean deleteResultFiles(String runId, LocalDateTime finishedAt) {
        try {
            URI train = URI.create(runnerTrainUrl);
            int port = train.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(train.getScheme()) ? 443 : 80;
            }
            String query = "runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8);
            if (finishedAt != null) {
                query += "&finishedAt=" + URLEncoder.encode(finishedAt.toString(), StandardCharsets.UTF_8);
            }
            String endpoint = new URI(train.getScheme(), null, train.getHost(), port,
                    "/api/runner/result/delete", null, null).toString();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "?" + query))
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JSONObject body = JSONUtil.parseObj(response.body());
            if (response.statusCode() == 404) {
                return false;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || !body.getBool("ok", false)) {
                throw new IllegalStateException(body.getStr("error", body.getStr("message", "删除训练文件失败")));
            }
            return body.getBool("deleted", false);
        } catch (Exception e) {
            throw new IllegalStateException("删除训练文件失败: " + e.getMessage(), e);
        }
    }

    /** 请求 Runner 停止指定训练进程及其子进程。 */
    public JSONObject stopByRunId(String runId) {
        Exception lastError = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                URI train = URI.create(runnerTrainUrl);
                int port = train.getPort();
                if (port < 0) {
                    port = "https".equalsIgnoreCase(train.getScheme()) ? 443 : 80;
                }
                String endpoint = new URI(train.getScheme(), null, train.getHost(), port,
                        "/api/runner/stop", null, null).toString();
                URI uri = URI.create(endpoint + "?runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8));
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> response = client.send(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                JSONObject body = JSONUtil.parseObj(response.body());
                if (response.statusCode() >= 200 && response.statusCode() < 300
                        && body.getBool("ok", false)) {
                    return body;
                }
                if (response.statusCode() != 404) {
                    throw new IllegalStateException(body.getStr("error", body.getStr("message", "停止训练失败")));
                }
                lastError = new IllegalStateException(body.getStr("message", "训练进程尚未注册"));
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("停止训练被中断", e);
            } catch (Exception e) {
                lastError = e;
                break;
            }
        }
        throw new IllegalStateException("Runner 未找到可停止的训练进程: "
                + (lastError == null ? runId : lastError.getMessage()), lastError);
    }

    /** Runner 不可达或重启丢失内存登记时，按唯一任务名从本机进程命令行终止整棵进程树。 */
    public JSONObject stopLocalProcessByRunId(String runId) {
        String key = StrUtil.trimToEmpty(runId);
        if (StrUtil.isBlank(key) || key.contains("/") || key.contains("\\") || ".".equals(key) || "..".equals(key)) {
            throw new IllegalStateException("非法训练任务名称");
        }
        List<ProcessHandle> matched = ProcessHandle.allProcesses()
                .filter(ProcessHandle::isAlive)
                .filter(p -> {
                    ProcessHandle.Info info = p.info();
                    String commandLine = info.commandLine().orElse("");
                    String args = String.join(" ", info.arguments().orElse(new String[0]));
                    String text = commandLine + " " + args;
                    return text.contains(key) && (text.contains("train.py") || text.contains("config.py")
                            || text.contains("--run-id") || text.contains("--work-dir"));
                })
                .toList();
        if (matched.isEmpty()) {
            throw new IllegalStateException("本机进程列表中也未找到任务 " + key);
        }
        List<Long> pids = new ArrayList<>();
        for (ProcessHandle root : matched) {
            pids.add(root.pid());
            List<ProcessHandle> descendants = root.descendants().toList();
            for (int i = descendants.size() - 1; i >= 0; i--) descendants.get(i).destroy();
            root.destroy();
        }
        try { Thread.sleep(800L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        for (ProcessHandle root : matched) {
            root.descendants().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            if (root.isAlive()) root.destroyForcibly();
        }
        try { Thread.sleep(300L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        boolean stopped = matched.stream().noneMatch(ProcessHandle::isAlive);
        if (!stopped) throw new IllegalStateException("已发送强制终止信号，但仍检测到存活进程");
        JSONObject result = new JSONObject();
        result.set("ok", true);
        result.set("stopped", true);
        result.set("source", "java_process_scan");
        result.set("pids", pids);
        return result;
    }

    private Path resolveLaunchScript() {
        Path configured = Path.of(runnerLaunchScript).toAbsolutePath().normalize();
        if (isWindows() && isStartRunnerSh(configured)) {
            Path windowsSibling = configured.resolveSibling("start_runner.cmd").toAbsolutePath().normalize();
            if (Files.isRegularFile(windowsSibling)) return windowsSibling;
        }
        if (Files.isRegularFile(configured)) return configured;
        for (Path candidate : launchScriptFallbacks(configured)) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return configured;
    }

    private Path resolveRunnerLogPath(Path script) {
        if (!isWindows() || !looksLikeUnixAbsolutePath(runnerAutoStartLog)) {
            return Path.of(runnerAutoStartLog).toAbsolutePath().normalize();
        }
        return script.getParent().resolve("..").resolve("logs").resolve("runner-manual-start.log").toAbsolutePath().normalize();
    }

    private boolean looksLikeUnixAbsolutePath(String rawPath) {
        if (rawPath == null) return false;
        String normalized = rawPath.replace('\\', '/');
        return normalized.startsWith("/home/") || normalized.startsWith("/opt/") || normalized.startsWith("/var/");
    }

    private List<Path> launchScriptFallbacks(Path configured) {
        List<Path> candidates = new ArrayList<>();
        if (isWindows()) {
            addSiblingWithName(candidates, configured, "start_runner.cmd");
            addSiblingWithName(candidates, configured, "start_runner.bat");
            addSiblingWithName(candidates, configured, "start_runner.ps1");
            candidates.add(Path.of("mmdet_run", "mmdet_runner_srv", "start_runner.cmd").toAbsolutePath().normalize());
            candidates.add(Path.of("..", "mmdet_run", "mmdet_runner_srv", "start_runner.cmd").toAbsolutePath().normalize());
        } else {
            addSiblingWithName(candidates, configured, "start_runner.sh");
            candidates.add(Path.of("mmdet_run", "mmdet_runner_srv", "start_runner.sh").toAbsolutePath().normalize());
            candidates.add(Path.of("..", "mmdet_run", "mmdet_runner_srv", "start_runner.sh").toAbsolutePath().normalize());
        }
        return candidates;
    }

    private void addSiblingWithName(List<Path> candidates, Path configured, String fileName) {
        Path parent = configured.getParent();
        if (parent != null) candidates.add(parent.resolve(fileName).toAbsolutePath().normalize());
    }

    private ProcessBuilder buildRunnerProcessBuilder(Path script) {
        String name = script.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isWindows()) {
            if (name.endsWith(".cmd") || name.endsWith(".bat")) return new ProcessBuilder("cmd.exe", "/c", script.toString());
            if (name.endsWith(".ps1")) return new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString());
        }
        return new ProcessBuilder("bash", script.toString());
    }

    private void applyWorkspaceRunnerEnv(ProcessBuilder pb) {
        Path workspace = WorkspacePathUtil.workspaceRoot();
        pb.environment().putIfAbsent("APP_WORKSPACE_ROOT", workspace.toString());
        pb.environment().putIfAbsent("MMDET_REPO_ROOT",
                workspace.resolve("mmdet_run").resolve("mmdetection-3.0.0").toString());
        pb.environment().putIfAbsent("MMDET_UPLOAD_ROOT",
                workspace.resolve("mmdet_run").resolve("myfiles").toString());
        pb.environment().putIfAbsent("MMDET_WORK_ROOT",
                workspace.resolve("artifacts").resolve("mmdet_runs").toString());
    }

    private boolean isStartRunnerSh(Path script) {
        Path fileName = script.getFileName();
        return fileName != null && "start_runner.sh".equalsIgnoreCase(fileName.toString());
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String tailLog(Path logFile, int maxChars) {
        try {
            if (!Files.isRegularFile(logFile)) return "";
            String text = Files.readString(logFile, StandardCharsets.UTF_8);
            return text.length() > maxChars ? text.substring(text.length() - maxChars) : text;
        } catch (IOException e) {
            return "读取 Runner 日志失败: " + e.getMessage();
        }
    }
    private URI runnerHealthUri() {
        URI train = URI.create(runnerTrainUrl);
        int port = train.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(train.getScheme()) ? 443 : 80;
        }
        try {
            return new URI(train.getScheme(), null, train.getHost(), port, "/health", null, null);
        } catch (Exception e) {
            throw new IllegalStateException("invalid sys.runner.train-url: " + runnerTrainUrl, e);
        }
    }

    /** 同步启动训练（等待 Python Runner 返回） */
    public RunnerTrainResponse startByRunId(String runId) {
        return startByRunId(runId, null);
    }

    /** 同步启动训练（等待 Python Runner 返回），可传 runner_mode/fixed 参数。 */
    public RunnerTrainResponse startByRunId(String runId, JSONObject runnerOptions) {
        // Runner 可能随后端刚拉起；略加长间隔以便自动启动完成健康检查后再连上
        int[] retryDelaysMs = {0, 2000, 5000};
        RunnerTrainResponse lastError = RunnerTrainResponse.transportError("runner not called");
        for (int i = 0; i < retryDelaysMs.length; i++) {
            if (retryDelaysMs[i] > 0) {
                try {
                    Thread.sleep(retryDelaysMs[i]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return RunnerTrainResponse.transportError("interrupted before retry");
                }
            }
            RunnerTrainResponse resp = callRunner(runId, runnerOptions);
            if (resp.isOk()) {
                return resp;
            }
            lastError = resp;
            log.warn("runner call failed (attempt {}/{}), runId={}, error={}",
                    i + 1, retryDelaysMs.length, runId, resp.getError());
        }
        return lastError;
    }

    private RunnerTrainResponse callRunner(String runId, JSONObject runnerOptions) {
        try {
            String url = runnerTrainUrl + "?runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            String json = runnerOptions == null ? "{}" : runnerOptions.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofMinutes(90))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.getBytes(StandardCharsets.UTF_8)))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(resp.statusCode(), resp.body());
        } catch (Exception e) {
            log.error("call python runner failed, runId={}", runId, e);
            return RunnerTrainResponse.transportError(e.getMessage());
        }
    }

    private RunnerTrainResponse parseResponse(int statusCode, String body) {
        RunnerTrainResponse result = new RunnerTrainResponse();
        result.setStatusCode(statusCode);
        result.setRawBody(body);
        try {
            JSONObject jo = JSONUtil.parseObj(body);
            result.setOk(jo.getBool("ok", statusCode >= 200 && statusCode < 300));
            result.setError(jo.getStr("error"));
            result.setWorkDir(jo.getStr("work_dir"));
            result.setLogPath(jo.getStr("log"));
            String resultsTxt = jo.getStr("results_txt");
            if (resultsTxt == null) {
                resultsTxt = jo.getStr("result_text");
            }
            result.setResultsTxt(resultsTxt);
        } catch (Exception ex) {
            result.setOk(statusCode >= 200 && statusCode < 300);
            result.setError("invalid runner response: " + ex.getMessage());
        }
        return result;
    }
}
