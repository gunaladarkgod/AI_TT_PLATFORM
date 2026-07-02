package com.xgls.web.runner;

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class TrainRunnerService {

    @Value("${sys.runner.train-url:http://127.0.0.1:8009/api/runner/train}")
    private String runnerTrainUrl;

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

    /** 由 Runner 使用 MMEngine Config 读取模板、写入参数并生成单一 config.py。 */
    public JSONObject generateConfig(JSONObject payload) {
        return postConfigJson("/api/config/generate", payload, Duration.ofMinutes(3));
    }

    /** 获取模板目录中实际存在/缺失的网络模板。 */
    public JSONObject getConfigTemplates() {
        return getConfigJson("/api/config/templates", Duration.ofSeconds(15));
    }

    /** 读取已生成的单文件配置摘要；includeText=true 时同时返回源码。 */
    public JSONObject readConfig(String runId, boolean includeText) {
        String query = "runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8)
                + "&includeText=" + includeText;
        return getConfigJson("/api/config/read?" + query, Duration.ofSeconds(30));
    }

    private JSONObject postConfigJson(String path, JSONObject payload, Duration timeout) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(runnerUri(path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
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
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(runnerUri(pathAndQuery))
                    .timeout(timeout).GET().build();
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
            RunnerTrainResponse resp = callRunner(runId);
            if (resp.isOk()) {
                return resp;
            }
            lastError = resp;
            log.warn("runner call failed (attempt {}/{}), runId={}, error={}",
                    i + 1, retryDelaysMs.length, runId, resp.getError());
        }
        return lastError;
    }

    private RunnerTrainResponse callRunner(String runId) {
        try {
            String url = runnerTrainUrl + "?runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(90))
                    .POST(HttpRequest.BodyPublishers.noBody())
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
            result.setClearmlTaskId(jo.getStr("clearml_task_id"));
        } catch (Exception ex) {
            result.setOk(statusCode >= 200 && statusCode < 300);
            result.setError("invalid runner response: " + ex.getMessage());
        }
        return result;
    }
}
