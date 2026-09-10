package com.xgls.web.engine;

import cn.hutool.json.JSONObject;
import com.xgls.web.runner.RunnerTrainResponse;
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
        return Map.of("ok", false, "message", "Ultralytics Runner 尚未启动；请在独立 Python 环境安装 ultralytics==" + VERSION
                + " 后启动 engines/yolo_run/ultralytics_runner/start_runner.cmd");
    }

    public RunnerTrainResponse start(String runId, JSONObject options) {
        String dataFormat = options == null ? null : options.getStr("data_format");
        if (!"yolo".equalsIgnoreCase(dataFormat)) {
            return RunnerTrainResponse.transportError("Ultralytics 仅支持 YOLO 格式实例数据集；请在数据集预处理中同时或单独导出 YOLO 格式");
        }
        return RunnerTrainResponse.transportError(availability().get("message").toString());
    }
    public JSONObject latestLog(String runId, int tailLines, JSONObject options) {
        throw new IllegalStateException(availability().get("message").toString());
    }
    public JSONObject stop(String runId) {
        throw new IllegalStateException(availability().get("message").toString());
    }
}
