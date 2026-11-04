package sail.framework.web;

import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import sail.common.core.*;

/** MVC 统一异常响应，不向外暴露 SQL、堆栈及文件系统路径。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> business(BusinessException ex) {
        // 日志携带追踪号和业务错误，不输出请求秘密或无意义的业务堆栈。
        log.warn(
                "业务请求失败 traceId={} code={} message={}",
                RequestContext.traceId(),
                ex.getCode(),
                ex.getMessage());
        return ResponseEntity.status(ex.getCode())
                .body(ApiResult.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResult<Void>> validation(Exception ex) {
        // 仅取字段路径和约束消息，不输出 rejectedValue、原始 JSON 或完整绑定异常。
        String message;
        if (ex instanceof MethodArgumentNotValidException invalid) {
            message =
                    invalid.getBindingResult().getFieldErrors().stream()
                            .map(field -> field.getField() + "：" + field.getDefaultMessage())
                            .distinct()
                            .collect(java.util.stream.Collectors.joining("；"));
        } else if (ex instanceof ConstraintViolationException invalid) {
            message =
                    invalid.getConstraintViolations().stream()
                            .map(field -> field.getPropertyPath() + "：" + field.getMessage())
                            .distinct()
                            .collect(java.util.stream.Collectors.joining("；"));
        } else if (ex instanceof HttpMessageNotReadableException) {
            message = "请求 JSON 格式或字段类型不正确";
        } else {
            message = "请求参数约束校验失败，请检查参数格式和范围";
        }
        log.warn(
                "参数校验失败 traceId={} type={} fields={}",
                RequestContext.traceId(),
                ex.getClass().getSimpleName(),
                message);
        return ResponseEntity.badRequest().body(ApiResult.error(400, message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> upload(MaxUploadSizeExceededException ex) {
        log.warn("上传大小超限 traceId={}", RequestContext.traceId());
        return ResponseEntity.status(413).body(ApiResult.error(413, "文件超过上传大小限制"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> unexpected(Exception ex) {
        // 系统异常只在服务端输出堆栈，以追踪号定位。
        log.error("请求处理失败 traceId={}", RequestContext.traceId(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResult.error(500, "服务暂不可用，请稍后重试"));
    }
}
