package com.xgls.platform.v2.api.v1.common;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Same shape as legacy {@code AjaxResult}: {@code code}, {@code msg}, {@code data}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AjaxResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int code;
    private final String msg;
    private final Object data;

    public AjaxResult(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static AjaxResult success() {
        return new AjaxResult(0, "请求成功", null);
    }

    public static AjaxResult success(Object data) {
        return new AjaxResult(0, "请求成功", data);
    }

    public static AjaxResult error(ErrorCode error) {
        return new AjaxResult(error.getCode(), error.getMsg(), null);
    }

    public static AjaxResult error(String msg) {
        return new AjaxResult(1, msg, null);
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public Object getData() {
        return data;
    }
}
