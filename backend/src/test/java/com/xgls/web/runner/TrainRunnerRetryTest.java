package com.xgls.web.runner;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TrainRunnerRetryTest {
    @Test
    void doesNotResubmitAfterRunnerReportsTrainingFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/runner/train", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"ok\":false,\"exit_code\":1,\"error\":\"checkpoint save failed\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            TrainRunnerService service = new TrainRunnerService();
            ReflectionTestUtils.setField(service, "runnerTrainUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/runner/train");

            RunnerTrainResponse response = service.startByRunId("failed-run");

            assertFalse(response.isOk());
            assertEquals("checkpoint save failed", response.getError());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }
}
