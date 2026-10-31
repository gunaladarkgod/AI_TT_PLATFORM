package com.xgls.web.engine;

import cn.hutool.json.JSONObject;
import com.xgls.web.runner.RunnerTrainResponse;
import com.xgls.web.runner.TrainRunnerService;
import com.xgls.web.service.WorkspaceComponentService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class MmdetTrainingEngineAdapter implements TrainingEngineAdapter {
    private final TrainRunnerService runner;
    private final WorkspaceComponentService components;
    public MmdetTrainingEngineAdapter(TrainRunnerService runner, WorkspaceComponentService components) {
        this.runner = runner; this.components = components;
    }
    public String engineId() { return "mmdet"; }
    public Set<String> supportedDataFormats() { return Set.of("coco"); }
    public Map<String, Object> availability() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> files = components.component("mmdet", "mmdet");
        if (!"ready".equals(files.get("status"))) {
            out.put("ok", false);
            out.put("message", files.get("message"));
            return out;
        }
        Map<String, Object> health = runner.probeHealth();
        out.put("ok", Boolean.TRUE.equals(health.get("ok")));
        out.put("message", health.getOrDefault("message", "MMDet Runner 状态未知"));
        out.put("health", health);
        return out;
    }
    public RunnerTrainResponse start(String runId, JSONObject options) { return runner.startByRunId(runId, options); }
    public JSONObject latestLog(String runId, int tailLines, JSONObject options) { return runner.getLatestTrainLog(runId, tailLines, options); }
    public JSONObject stop(String runId) { return runner.stopByRunId(runId); }
}
