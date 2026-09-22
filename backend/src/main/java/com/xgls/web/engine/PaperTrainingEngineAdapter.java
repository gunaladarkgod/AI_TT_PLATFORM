package com.xgls.web.engine;

import cn.hutool.json.JSONObject;
import com.xgls.web.runner.RunnerTrainResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** Paper sources are enabled only after a research baseline pins an upstream commit and entry point. */
@Component
public class PaperTrainingEngineAdapter implements TrainingEngineAdapter {
    public String engineId() { return "paper"; }
    public Set<String> supportedDataFormats() { return Set.of("coco", "yolo", "custom"); }
    public Map<String, Object> availability() { return Map.of("ok", false, "message", "论文引擎需要研究基线固定上游 Git 提交和训练入口后才能运行"); }
    public RunnerTrainResponse start(String runId, JSONObject options) { return RunnerTrainResponse.transportError("论文引擎尚未绑定已固定版本的研究基线"); }
    public JSONObject latestLog(String runId, int tailLines, JSONObject options) { throw new IllegalStateException("论文引擎尚未绑定已固定版本的研究基线"); }
    public JSONObject stop(String runId) { throw new IllegalStateException("论文引擎尚未绑定已固定版本的研究基线"); }
}
