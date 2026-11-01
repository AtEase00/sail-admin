package sail.framework.web;

import lombok.RequiredArgsConstructor;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import sail.common.core.*;

/** 在统一响应序列化前保留脱敏结果，避免缓存和缓冲文件下载响应。 */
@RestControllerAdvice
@RequiredArgsConstructor
public class AuditResponseAdvice implements ResponseBodyAdvice<Object> {
    private final LogSanitizer sanitizer;

    @Override
    public boolean supports(MethodParameter parameter, Class converterType) {
        // 最终是否为业务响应在 beforeBodyWrite 判断，兼容异常处理器返回 ResponseEntity。
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter parameter,
            MediaType mediaType,
            Class converterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        var context = RequestAuditContext.current();
        if (context != null && body instanceof ApiResult<?> result) {
            // 返回客户端的原始数据不变，只有日志副本执行脱敏。
            context.setResponseBody(sanitizer.value(result));
            context.setResponseCode(result.code());
            if (result.code() != 0) context.setErrorMessage(result.message());
        }
        return body;
    }
}
