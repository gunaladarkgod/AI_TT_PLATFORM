package com.xgls.web.base;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 操作消息提醒
 * 
 */
@Data
@AllArgsConstructor
@Schema(description = "响应返回数据对象")
public class AjaxResult implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(title = "code", description = "响应码", requiredMode = Schema.RequiredMode.REQUIRED)
	private Integer code;

	@Schema(title = "msg", description = "响应信息", example = "操作成功", requiredMode = Schema.RequiredMode.REQUIRED)
	private String msg;

	@Schema(title = "data", description = "响应数据")
	private Object data;

	public static AjaxResult success() {
		return new AjaxResult(0, "请求成功", null);
	}

	public static AjaxResult success(Object data) {
		return new AjaxResult(0, "请求成功", data);
	}

	public static AjaxResult error() {
		return new AjaxResult(1, "请求失败", null);
	}

	public static AjaxResult error(String msg) {
		return new AjaxResult(1, msg, null);
	}

	public static AjaxResult error(String msg, Object data) {
		return new AjaxResult(1, msg, data);
	}

	public static AjaxResult error(ErrorCode infos) {
		return new AjaxResult(infos.getCode(), infos.getMsg(), null);
	}

	public static AjaxResult error(ErrorCode infos, Object data) {
		return new AjaxResult(infos.getCode(), infos.getMsg(), data);
	}

	public boolean isSuccess() {
		return code == 0;
	}
}
