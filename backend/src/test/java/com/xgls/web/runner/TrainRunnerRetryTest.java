package com.xgls.web.runner;

import cn.hutool.json.JSONObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TrainRunnerRetryTest {
    @Test void completedFailureOrAmbiguousResponseMustNeverReplayTraining() throws Exception {
        for (String body : new String[]{"{\"ok\":false,\"error\":\"checkpoint save failed\"}", "not json", "{}"}) {
            AtomicInteger calls = new AtomicInteger();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/runner/train", exchange -> {
                calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
            try {
                TrainRunnerService service = new TrainRunnerService();
                ReflectionTestUtils.setField(service,"runnerTrainUrl","http://127.0.0.1:"+server.getAddress().getPort()+"/api/runner/train");
                RunnerTrainResponse result = service.startByRunId("中文任务", new JSONObject());
                assertFalse(result.isOk()); assertFalse(result.isRetryable());
                assertEquals(1, calls.get(), "A training failure must not run the entire task again");
            } finally { server.stop(0); }
        }
    }
}
