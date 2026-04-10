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
                + (error != null ? ", error=" + error : "");
    }
}
