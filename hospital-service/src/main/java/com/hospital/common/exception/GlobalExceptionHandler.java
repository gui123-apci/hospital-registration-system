package com.hospital.common.exception;

import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局异常处理，避免异常细节直接暴露给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败。
     * Spring 会把所有字段的违规一次性收进 BindingResult，这里全部拼出来返回，
     * 避免用户改一个、再被下一个拦住，来回好几轮。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleMethodArgumentNotValid(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("；"));
        return Result.fail(ResultCode.PARAM_ERROR.getCode(),
                message.isBlank() ? ResultCode.PARAM_ERROR.getMsg() : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .distinct()
                .collect(Collectors.joining("；"));
        return Result.fail(ResultCode.PARAM_ERROR.getCode(),
                message.isBlank() ? ResultCode.PARAM_ERROR.getMsg() : message);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public Result<Void> handleParamException(Exception e) {
        log.warn("请求参数异常: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.fail(ResultCode.FORBIDDEN);
    }

    /**
     * 数据库唯一键冲突（并发窗口里两个请求同时通过业务预查，后插入的被数据库拦下）。
     * 不接的话会掉进兜底 Exception，用户看到"系统繁忙"，实际是"数据已存在"。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return Result.fail(ResultCode.DATA_CONFLICT);
    }

    /**
     * 路径不存在。不处理的话会落到兜底 Exception，被误报成“系统繁忙”。
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Result<Void> handleNotFound(Exception e) {
        return Result.fail(ResultCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
