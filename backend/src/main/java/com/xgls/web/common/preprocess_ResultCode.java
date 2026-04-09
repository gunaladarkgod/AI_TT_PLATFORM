package com.xgls.web.common;

public enum preprocess_ResultCode {
    SUCCESS(200, "成功"),
    ERROR(500, "失败");

    public final Integer code;
    public final String message;

    preprocess_ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
