package com.xgls.web.security;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authz.UnauthenticatedException;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.xgls.web.base.AjaxResult;
import com.xgls.web.base.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 全局异常处理类
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(RuntimeException.class)
	public AjaxResult runtimeException(RuntimeException e) {
		log.error(e.getMessage());
		return AjaxResult.error(ErrorCode.FAILED);
	}
	/** 权限不足 */
	@ExceptionHandler({ UnauthorizedException.class, UnauthenticatedException.class, AuthenticationException.class })
	public AjaxResult unauthorizedException(UnauthorizedException e) {
		log.error(e.getMessage());
		return AjaxResult.error(ErrorCode.PERMISSION_DENIED);
	}

	/** 参数错误 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public AjaxResult methodArgumentNotValidException(MethodArgumentNotValidException e) {
		FieldError fieldError = e.getBindingResult().getFieldError();
		if (fieldError != null) {
			String msg = fieldError.getDefaultMessage();
			return AjaxResult.error(msg);
		}
		return AjaxResult.error(ErrorCode.PARAMS_WRONG);
	}


}
