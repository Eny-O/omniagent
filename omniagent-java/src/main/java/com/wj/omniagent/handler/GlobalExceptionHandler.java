package com.wj.omniagent.handler;

import com.wj.omniagent.common.enums.ErrorCodeEnum;
import com.wj.omniagent.common.pojo.result.ResponseResult;
import com.wj.omniagent.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseResult<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return ResponseResult.error(e.getErrorCode(), e.getMessage());
    }

    /**
     * 请求体参数校验失败（@RequestBody + @Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败：{}", message);
        return ResponseResult.error(ErrorCodeEnum.BAD_REQUEST.getCode(), message);
    }

    /**
     * 表单参数绑定失败
     */
    @ExceptionHandler(BindException.class)
    public ResponseResult<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败：{}", message);
        return ResponseResult.error(ErrorCodeEnum.BAD_REQUEST.getCode(), message);
    }

    /**
     * 方法参数校验失败（@RequestParam / @PathVariable + @Validated）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败：{}", message);
        return ResponseResult.error(ErrorCodeEnum.BAD_REQUEST.getCode(), message);
    }

    /**
     * 非法参数（Assert / Service 内主动抛出的 IllegalArgumentException）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseResult<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数：{}", e.getMessage());
        return ResponseResult.error(ErrorCodeEnum.BAD_REQUEST.getCode(), e.getMessage());
    }

    /**
     * 静态资源不存在
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseResult<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.debug("静态资源不存在：{}", e.getResourcePath());
        return ResponseResult.error(ErrorCodeEnum.NOT_FOUND.getCode(), "资源不存在");
    }

    /**
     * Spring Security 认证失败（未登录/凭据错误）
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseResult<Void> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败：{}", e.getMessage());
        return ResponseResult.error(ErrorCodeEnum.UNAUTHORIZED);
    }

    /**
     * Spring Security 鉴权失败（已登录但权限不足）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseResult<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足：{}", e.getMessage());
        return ResponseResult.error(ErrorCodeEnum.FORBIDDEN);
    }

    /**
     * 兜底：未知系统异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseResult<Void> handleException(Exception e) {
        log.error("系统异常：", e);
        return ResponseResult.error(ErrorCodeEnum.INTERNAL_ERROR);
    }
}
