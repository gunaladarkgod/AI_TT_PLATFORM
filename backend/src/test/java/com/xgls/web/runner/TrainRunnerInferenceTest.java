package com.xgls.web.runner;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TrainRunnerInferenceTest {
    private HttpServer server;
    private TrainRunnerService service;
    private int status = 200;
    private String response = "{\"ok\":true,\"detections\":[]}";
    private final AtomicReference<String> upgrade = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();

    @BeforeEach
    void startRunnerStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/runner/result/infer", exchange -> {
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            query.set(exchange.getRequestURI().getQuery());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        service = new TrainRunnerService();
        ReflectionTestUtils.setField(service, "runnerTrainUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/runner/train");
    }

    @AfterEach
    void stopRunnerStub() {
        if (server != null) server.stop(0);
    }

    @Test
    void largeImageUsesHttp1WithoutUpgradeAndPreservesResultSelection() {
        JSONObject payload = new JSONObject().set("image_base64", "A".repeat(200000));
        JSONObject options = new JSONObject().set("runner_work_root", "artifacts/mmdet_runs");
        JSONObject result = service.inferResult("测试模型", LocalDateTime.of(2026, 9, 27, 12, 30), options, payload);
        assertTrue(result.getBool("ok"));
        assertNull(upgrade.get(), "Runner must not receive an h2c upgrade request");
        assertEquals(payload.getStr("image_base64"), JSONUtil.parseObj(requestBody.get()).getStr("image_base64"));
        assertTrue(query.get().contains("runId=测试模型"));
        assertTrue(query.get().contains("finishedAt=2026-09-27T12:30"));
        assertTrue(query.get().contains("workRoot=artifacts/mmdet_runs"));
    }

    @Test
    void plainTextErrorRetainsHttpStatusAndRunnerCause() {
        status = 400;
        response = "Invalid HTTP request received.";
        String message = failure();
        assertTrue(message.contains("HTTP 400"));
        assertTrue(message.contains(response));
        assertFalse(message.contains("A JSONObject text"));
    }

    @Test
    void emptyResponseHasClearError() {
        status = 502;
        response = "";
        assertTrue(failure().contains("HTTP 502）：空响应"));
    }

    @Test
    void malformedObjectDoesNotLeakParserError() {
        response = "{invalid";
        assertTrue(failure().contains("Runner 返回无效 JSON 对象（HTTP 200）"));
    }

    @Test
    void jsonArrayIsNotAnInferenceResponse() {
        response = "[]";
        assertTrue(failure().contains("Runner 返回无效 JSON 对象"));
    }

    @Test
    void structuredInferenceFailureKeepsOriginalCause() {
        status = 400;
        response = "{\"ok\":false,\"error\":\"no trained checkpoint found for this result\"}";
        assertEquals("模型推理失败: no trained checkpoint found for this result", failure());
    }

    @Test
    void oversizedErrorIsSummarized() {
        status = 500;
        response = "Internal error\n" + "x".repeat(2000);
        String message = failure();
        assertTrue(message.contains("HTTP 500"));
        assertTrue(message.length() < 600);
        assertFalse(message.contains("\n"));
    }

    private String failure() {
        return assertThrows(IllegalStateException.class,
                () -> service.inferResult("test", null, null, new JSONObject())).getMessage();
    }
}
