package sail.framework.security;

import cn.dev33.satoken.exception.*;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;

/** 认证授权异常与通用业务错误保持相同响应契约。 */
@lombok.extern.slf4j.Slf4j
@Order(-10)
@RestControllerAdvice
public class SecurityExceptionHandler {
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResult<Void>> login(NotLoginException ex) {
        log.warn("登录校验失败 traceId={}", sail.common.core.RequestContext.traceId());
        return ResponseEntity.status(401).body(ApiResult.error(401, "登录已失效，请重新登录"));
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<ApiResult<Void>> permission(Exception ex) {
        log.warn(
                "权限校验失败 traceId={} type={}",
                sail.common.core.RequestContext.traceId(),
                ex.getClass().getSimpleName());
        return ResponseEntity.status(403).body(ApiResult.error(403, "没有此操作权限"));
    }
}
