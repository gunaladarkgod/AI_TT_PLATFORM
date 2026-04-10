package com.xgls.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.xgls.web.entity.TrainTask;
import com.xgls.web.runner.RunnerTrainResponse;
import org.junit.jupiter.api.Test;

class TrainTaskServiceTest {

    @Test
    void applyRunnerResultShouldReturnErrorWhenResponseIsNull() {
        TrainTaskService service = new TrainTaskService();
        String result = service.applyRunnerResult(null, null, "unit-test");
        assertEquals("runner:error, error=empty response", result);
    }

    @Test
    void applyRunnerResultShouldReturnSummaryWhenTaskIsNull() {
        TrainTaskService service = new TrainTaskService();
        RunnerTrainResponse response = new RunnerTrainResponse();
        response.setOk(true);
        response.setWorkDir("/tmp/work");

        String result = service.applyRunnerResult(null, response, "unit-test");
        assertEquals(response.summary(), result);
        assertTrue(result.contains("runner:success"));
    }

    @Test
    void applyRunnerResultShouldSaveCocoWhenRawBodyIsValidJson() {
        TrainTaskService service = spy(new TrainTaskService());
        TrainTask task = new TrainTask();
        task.setId(1);
        task.setName("run_001");

        RunnerTrainResponse response = new RunnerTrainResponse();
        response.setOk(true);
        response.setRawBody("{\"ok\":true,\"results_txt\":\"mAP:0.12 AP50:0.34\"}");

        doNothing().when(service).saveCocoResultFromRunner(eq(task), any(cn.hutool.json.JSONObject.class));

        String result = service.applyRunnerResult(task, response, "unit-test");

        verify(service).saveCocoResultFromRunner(eq(task), any(cn.hutool.json.JSONObject.class));
        assertEquals(response.summary(), result);
    }

    @Test
    void applyRunnerResultShouldIgnoreInvalidRawBody() {
        TrainTaskService service = spy(new TrainTaskService());
        TrainTask task = new TrainTask();
        task.setId(2);
        task.setName("run_002");

        RunnerTrainResponse response = new RunnerTrainResponse();
        response.setOk(false);
        response.setError("transport");
        response.setRawBody("not-a-json");

        String result = service.applyRunnerResult(task, response, "unit-test");

        verify(service, never()).saveCocoResultFromRunner(eq(task), any(cn.hutool.json.JSONObject.class));
        assertEquals(response.summary(), result);
    }
}
