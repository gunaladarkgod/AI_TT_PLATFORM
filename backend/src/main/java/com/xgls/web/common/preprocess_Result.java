package com.xgls.web.common;


import lombok.Data;

@Data
public class preprocess_Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> preprocess_Result<T> success(T data) {
        preprocess_Result<T> r = new preprocess_Result<>();
        r.setCode(preprocess_ResultCode.SUCCESS.code);
        r.setMsg(preprocess_ResultCode.SUCCESS.message);
        r.setData(data);
        return r;
    }

    public static <T> preprocess_Result<T> error(String msg) {
        preprocess_Result<T> r = new preprocess_Result<>();
        r.setCode(preprocess_ResultCode.ERROR.code);
        r.setMsg(msg);
        return r;
    }
}
