package com.xgls.web.runner;

import lombok.extern.slf4j.Slf4j;
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
    public String startByRunId(String runId) {
        try {
            String url = runnerTrainUrl + "?runId=" + URLEncoder.encode(runId, StandardCharsets.UTF_8);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(60))   // 训练脚本内部会快速返回started/完成文本
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.body(); // 形如 {"ok":true,"work_dir":"...","log":"...","result_text":"..."}
        } catch (Exception e) {
            log.error("call python runner failed, runId={}", runId, e);
            String msg = e.toString().replace("\\", "\\\\").replace("\"", "\\\"");
            return "{\"ok\":false,\"error\":\"" + msg + "\"}";
        }
    }
}
