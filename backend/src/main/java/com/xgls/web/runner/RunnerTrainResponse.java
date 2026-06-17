package com.xgls.web.runner;

import lombok.Data;

@Data
public class RunnerTrainResponse {
    private boolean ok;
    private Integer statusCode;
    private String error;
    private String workDir;
    private String logPath;
    private String resultsTxt;
    private String rawBody;
    /** Python Runner 返回的 ClearML 任务 ID（若本轮训练已挂载 ClearML） */
    private String clearmlTaskId;

    public static RunnerTrainResponse transportError(String error) {
        RunnerTrainResponse resp = new RunnerTrainResponse();
        resp.setOk(false);
        resp.setStatusCode(500);
        resp.setError(error);
        return resp;
    }

    public String summary() {
        String state = ok ? "success" : "error";
        return "runner:" + state
                + (workDir != null ? ", workDir=" + workDir : "")
                + (logPath != null ? ", log=" + logPath : "")
                + (resultsTxt != null ? ", result=" + resultsTxt : "")
                + (clearmlTaskId != null ? ", clearml=" + clearmlTaskId : "")
                + (error != null ? ", error=" + error : "");
    }
}
