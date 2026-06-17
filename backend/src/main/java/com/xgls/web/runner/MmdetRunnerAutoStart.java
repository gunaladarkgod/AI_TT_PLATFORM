package com.xgls.web.runner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 可选：与 Spring Boot 同进程生命周期内拉起 MMDet Runner（Python uvicorn）。
 * <p>
 * 生产环境更推荐 Docker Compose / systemd 单独托管 Runner；此处适合本机一键开发。
 * 默认开启（application.yml）；关闭：RUNNER_AUTO_START=false。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "sys.runner.auto-start", havingValue = "true")
public class MmdetRunnerAutoStart implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    @Value("${sys.runner.train-url:http://127.0.0.1:8009/api/runner/train}")
    private String trainUrl;

    @Value("${sys.runner.launch-script}")
    private String launchScript;

    @Value("${sys.runner.auto-start-log}")
    private String autoStartLogPath;

    /** 为 false 时，停止后端 JVM 不结束已拉起的 Runner（适合 IDE 反复调试；需彻底退出可设 true 或手动杀8009） */
    @Value("${sys.runner.auto-stop-on-shutdown:false}")
    private boolean autoStopOnShutdown;

    private final AtomicReference<Process> processRef = new AtomicReference<>();

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (runnerRespondedOk()) {
            log.info("[runner-autostart] 检测到 Runner 已可用，跳过启动（{}）", healthUri());
            return;
        }
        Path script = Path.of(launchScript).toAbsolutePath().normalize();
        if (!Files.isRegularFile(script)) {
            log.error("[runner-autostart] 启动脚本不存在: {}（可设置 sys.runner.launch-script）", script);
            return;
        }
        Path logFile = Path.of(autoStartLogPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            log.warn("[runner-autostart] 无法创建日志目录: {}", e.getMessage());
        }
        ProcessBuilder pb = new ProcessBuilder("bash", script.toString());
        pb.directory(script.getParent().toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        // 与子进程 uvicorn 一致：避免 Python 混入 ~/.local 中与 numpy/pandas 冲突导致 clearml 无法 import
        pb.environment().putIfAbsent("PYTHONNOUSERSITE", "1");
        try {
            Process p = pb.start();
            processRef.set(p);
            log.info("[runner-autostart] 已启动 MMDet Runner 子进程，pid={}，日志: {}；停止后端时{}结束 Runner（sys.runner.auto-stop-on-shutdown={}）",
                    p.pid(), logFile, autoStopOnShutdown ? "将" : "默认不", autoStopOnShutdown);
            waitUntilRunnerHealthyOrTimeout(p, logFile);
        } catch (IOException e) {
            log.error("[runner-autostart] 启动失败: {}", e.getMessage());
        }
    }

    /**
     * uvicorn 绑定端口需要时间；就绪后再接受训练 HTTP，否则前端「发布」会立刻失败。
     */
    private void waitUntilRunnerHealthyOrTimeout(Process child, Path logFile) {
        final int maxWaitSeconds = 45;
        final long deadline = System.currentTimeMillis() + maxWaitSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!child.isAlive()) {
                log.error("[runner-autostart] Runner 进程已退出（pid={}）。请查看日志: {}", child.pid(), logFile);
                return;
            }
            if (runnerRespondedOk()) {
                log.info("[runner-autostart] Runner 已通过 GET /health（{}），可接收训练请求", healthUri());
                return;
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn(
                "[runner-autostart] {} 秒内健康检查仍未通过（{}）。训练可能暂时失败；请检查日志: {}",
                maxWaitSeconds,
                healthUri(),
                logFile);
        tailLogPreview(logFile);
    }

    private void tailLogPreview(Path logFile) {
        try {
            if (!Files.isRegularFile(logFile)) {
                return;
            }
            byte[] raw = Files.readAllBytes(logFile);
            String text = new String(raw, StandardCharsets.UTF_8);
            String tail = text.length() > 1200 ? text.substring(text.length() - 1200) : text;
            log.warn("[runner-autostart] 日志末尾预览:\n{}", tail);
        } catch (IOException ignored) {
        }
    }

    private URI healthUri() {
        URI train = URI.create(trainUrl);
        int port = train.getPort();
        if (port < 0) {
            port = "https".equalsIgnoreCase(train.getScheme()) ? 443 : 80;
        }
        try {
            return new URI(train.getScheme(), null, train.getHost(), port, "/health", null, null);
        } catch (Exception e) {
            throw new IllegalStateException("invalid sys.runner.train-url: " + trainUrl, e);
        }
    }

    private boolean runnerRespondedOk() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder(healthUri()).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void destroy() {
        if (!autoStopOnShutdown) {
            log.info("[runner-autostart] auto-stop-on-shutdown=false，保留 MMDet Runner进程（端口与 sys.runner.train-url 一致）");
            processRef.set(null);
            return;
        }
        Process p = processRef.getAndSet(null);
        if (p == null) {
            return;
        }
        if (!p.isAlive()) {
            return;
        }
        log.info("[runner-autostart] 正在停止 MMDet Runner 子进程 pid={}", p.pid());
        p.destroy();
        try {
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }
}
