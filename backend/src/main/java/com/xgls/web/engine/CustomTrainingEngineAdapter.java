package com.xgls.web.engine;

import cn.hutool.json.JSONObject;
import com.xgls.web.runner.RunnerTrainResponse;
import com.xgls.web.runner.TrainRunnerService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** External source execution. The platform passes a reviewed command only; it never edits external source code. */
@Component
public class CustomTrainingEngineAdapter implements TrainingEngineAdapter {
    private final TrainRunnerService runner;
    public CustomTrainingEngineAdapter(TrainRunnerService runner) { this.runner = runner; }
    public String engineId() { return "custom"; }
    public Set<String> supportedDataFormats() { return Set.of("coco", "yolo", "custom"); }
    public Map<String, Object> availability() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> health = runner.probeHealth();
        out.put("ok", Boolean.TRUE.equals(health.get("ok")));
        out.put("message", health.getOrDefault("message", "外部命令 Runner 状态未知"));
        out.put("health", health);
        return out;
    }
    public RunnerTrainResponse start(String runId, JSONObject options) { return runner.startByRunId(runId, options); }
    public JSONObject latestLog(String runId, int tailLines, JSONObject options) { return runner.getLatestTrainLog(runId, tailLines, options); }
    public JSONObject stop(String runId) { return runner.stopByRunId(runId); }
}
