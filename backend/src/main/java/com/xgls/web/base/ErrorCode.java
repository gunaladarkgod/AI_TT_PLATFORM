package com.xgls.web.base;

import lombok.Getter;

@Getter
public enum ErrorCode {

	SUCCESS(0, "请求成功"),
	FAILED(1, "请求失败"),

	AUTH_FAILED(2, "认证失败"),
	PERMISSION_DENIED(3, "权限不足"),
	PARAMS_WRONG(4, "请求参数错误"),

	ACCOUNT_NOT_EXIST(5, "账户不存在"),
	ACCOUNT_HAS_EXIST(6, "账户已存在"),
	ACCOUNT_PWD_WRONG(7, "账户密码不正确"),
	ACCOUNT_PWD_WEAK(7, "密码强度太低"),
	ACCOUNT_LOCKED(8, "账户锁定"),
	ACCOUNT_CANT_DEL(9, "基础账户不允许删除"),

	USER_NOT_EXIST(10, "用户不存在"),
	USER_HAS_EXIST(11, "用户已存在"),
	NAME_HAS_EXIST(12, "名称已存在"),
	RECORD_NOT_EXIST(13, "记录不存在"),

	FILE_EMPTY(101, "文件为空"),
	FILE_OUT_OF_SIZE(102, "文件超出大小"),
	FILE_FMT_WRONG(103, "文件格式不正确"),
	FILE_IO_EXCEPTION(104, "文件操作异常"),
	FILE_MKDIR_FAILE(105, "创建目录失败"),
	FILE_MKFILE_FAILE(106, "创建文件失败"),
	FILE_NAME_EXIST(107, "文件名称已存在"),
	FILE_NAME_EMPTY(108, "文件名称不存在"),

	MENU_NAME_EXIST(201, "菜单名称已存在"),
	MENU_URL_EXIST(202, "菜单路径已存在"),
	MENU_NOT_EXIST(203, "菜单不存在"),


	UNKNOW_ERROR(999, "发生错误");

	ErrorCode(Integer code, String msg) {
		this.code = code;
		this.msg = msg;
	}

	private Integer code;

	private String msg;

}
