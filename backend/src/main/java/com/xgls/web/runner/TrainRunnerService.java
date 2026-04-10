package com.xgls.web.runner;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class TrainRunnerService {

    @Value("${sys.runner.train-url:http://127.0.0.1:8009/api/runner/train}")
    private String runnerTrainUrl;

    /** 同步启动训练（等待 Python Runner 返回） */
    public RunnerTrainResponse startByRunId(String runId) {
        int[] retryDelaysMs = {0, 1000};
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
        } catch (Exception ex) {
            result.setOk(statusCode >= 200 && statusCode < 300);
            result.setError("invalid runner response: " + ex.getMessage());
        }
        return result;
    }
}
