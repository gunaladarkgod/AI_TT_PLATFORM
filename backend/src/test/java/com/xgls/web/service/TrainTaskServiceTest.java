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
    @Test void componentIdentityIsForwardedToRunner() {
        TrainTaskService service = spy(new TrainTaskService());
        TrainTask task = new TrainTask(); task.setId(18); task.setType("ultralytics");
        org.mockito.Mockito.doReturn(task).when(service).getById(18);
        var mapper = org.mockito.Mockito.mock(com.xgls.web.mapper.TrainExtMapper.class);
        var ext = new com.xgls.web.entity.TrainExt();
        ext.setParams("{\"engine\":\"ultralytics\",\"component_training\":true,\"component_id\":\"small-object/yolo11\",\"component_kind\":\"baseline\",\"component_commit\":\"sha\",\"adapter_digest\":\"digest\"}");
        org.mockito.Mockito.when(mapper.selectById(18)).thenReturn(ext);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"trainExtMapper",mapper);
        var options = service.runnerOptionsForTask(18);
        assertTrue(options.getBool("component_training"));
        assertEquals("small-object/yolo11", options.getStr("component_id"));
        assertEquals("sha", options.getStr("component_commit"));
        assertEquals("digest", options.getStr("adapter_digest"));
    }

}
