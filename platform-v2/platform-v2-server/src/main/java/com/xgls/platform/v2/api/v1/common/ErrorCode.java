package com.xgls.platform.v2.api.v1.common;

public enum ErrorCode {

    SUCCESS(0, "请求成功"),
    FAILED(1, "请求失败"),
    AUTH_FAILED(2, "认证失败"),
    PERMISSION_DENIED(3, "权限不足"),
    PARAMS_WRONG(4, "请求参数错误"),
    ACCOUNT_PWD_WRONG(7, "账户密码不正确"),
    ACCOUNT_LOCKED(8, "账户锁定");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
