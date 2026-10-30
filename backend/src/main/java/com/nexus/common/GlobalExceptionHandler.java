package com.nexus.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：客户端只看到规范化错误结构，不泄露堆栈与内部细节。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException e) {
        return ResponseEntity.status(mapStatus(e.getErrorCode()))
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getField() + " " + fe.getDefaultMessage() : "参数校验失败";
        return ResponseEntity.badRequest().body(ApiResponse.error(400, msg));
    }

    /**
     * 请求体读不出来（JSON 语法错、日期格式不对等）。
     * 这本质是客户端的问题，不能落到 500 —— 否则用户看到「服务器内部错误」，
     * 完全不知道是自己把日期写成了 2026/11/02。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "请求参数格式不正确（日期请用 yyyy-MM-dd）"));
    }

    /**
     * 唯一约束/外键冲突。落到通用处理器会变成「服务器内部错误」，
     * 而这绝大多数是「重复提交」——告诉客户端冲突，比让他猜要强。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("数据冲突：{}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONFLICT.getCode(), "数据冲突：可能重复添加了同样的内容"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }

    private HttpStatus mapStatus(ErrorCode code) {
        switch (code) {
            case NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case CONFLICT:
                return HttpStatus.CONFLICT;
            case AGENT_ERROR:
                return HttpStatus.BAD_GATEWAY;
            case BAD_REQUEST:
            default:
                return HttpStatus.BAD_REQUEST;
        }
    }
}
