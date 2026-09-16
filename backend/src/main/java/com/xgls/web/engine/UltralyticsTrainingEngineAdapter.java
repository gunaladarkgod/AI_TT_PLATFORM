package com.xgls.web.engine;

import cn.hutool.json.JSONObject;
import com.xgls.web.runner.RunnerTrainResponse;
import com.xgls.web.runner.TrainRunnerService;
import com.xgls.web.utils.WorkspacePathUtil;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Official Ultralytics runtime. It is deliberately isolated from the MMDet environment. */
@Component
public class UltralyticsTrainingEngineAdapter implements TrainingEngineAdapter {
    static final String VERSION = "8.4.115";
    private final TrainRunnerService runner;

    public UltralyticsTrainingEngineAdapter(TrainRunnerService runner) {
        this.runner = runner;
    }

    public String engineId() { return "ultralytics"; }
    public Set<String> supportedDataFormats() { return Set.of("yolo"); }

    public Map<String, Object> availability() {
        Path root = WorkspacePathUtil.workspaceRoot().resolve("engines").resolve("yolo_run");
        Path requirements = root.resolve("ultralytics_runner").resolve("requirements.txt");
        if (!Files.isDirectory(root)) {
            return Map.of("ok", false, "message", "未找到 Ultralytics 引擎目录：" + root);
        }
        if (!Files.isRegularFile(requirements)) {
            return Map.of("ok", false, "message", "Ultralytics 运行时声明缺失：" + requirements);
        }
        Map<String, Object> health = runner.probeHealth();
        return Map.of(
                "ok", Boolean.TRUE.equals(health.get("ok")),
                "message", health.getOrDefault("message", "统一 Runner 状态未知"),
                "health", health,
                "requiredPackage", "ultralytics==" + VERSION,
                "environment", "每个训练任务创建时指定的 Python/Conda 环境"
        );
    }

    public RunnerTrainResponse start(String runId, JSONObject options) {
        String dataFormat = options == null ? null : options.getStr("data_format");
        if (!"yolo".equalsIgnoreCase(dataFormat)) {
            return RunnerTrainResponse.transportError("Ultralytics 仅支持 YOLO 格式实例数据集；请在数据集预处理中同时或单独导出 YOLO 格式");
        }
        return runner.startByRunId(runId, options);
    }
    public JSONObject latestLog(String runId, int tailLines, JSONObject options) {
        return runner.getLatestTrainLog(runId, tailLines, options);
    }
    public JSONObject stop(String runId) {
        return runner.stopByRunId(runId);
    }
}
