package com.xgls.web.engine;

import cn.hutool.json.JSONObject;
import com.xgls.web.runner.RunnerTrainResponse;

import java.util.Map;
import java.util.Set;

/** Stable boundary between platform scheduling and a concrete training engine. */
public interface TrainingEngineAdapter {
    String engineId();
    Set<String> supportedDataFormats();
    Map<String, Object> availability();
    RunnerTrainResponse start(String runId, JSONObject options);
    JSONObject latestLog(String runId, int tailLines, JSONObject options);
    JSONObject stop(String runId);
}
