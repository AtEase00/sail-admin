package sail.framework.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import sail.common.core.*;
import sail.common.web.RequestHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 请求全链路追踪，记录脱敏输入、真实结果、错误和响应耗时。 */
@Slf4j
@RequiredArgsConstructor
public class RequestTraceFilter extends OncePerRequestFilter {
    private final boolean requestLogEnabled;
    private final ApplicationEventPublisher events;
    private final LogSanitizer sanitizer;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String trace = UUID.randomUUID().toString().replace("-", "");
        RequestContext.set(trace);
        MDC.put("traceId", trace);
        var audit = RequestAuditContext.begin();
        response.setHeader(RequestHeaders.TRACE_ID, trace);
        String path = request.getRequestURI().replace('\r', '_').replace('\n', '_');
        boolean json =
                request.getContentType() != null
                        && request.getContentType()
                                .toLowerCase(java.util.Locale.ROOT)
                                .contains("json");
        // 仅缓存被业务实际读取的 JSON 输入，限制缓存大小，不提前消耗流和附件。
        var wrapped =
                new ContentCachingRequestWrapper(request, RequestLogConstants.MAX_BODY_LENGTH);
        long startedAt = System.nanoTime();
        boolean completed = false;
        try {
            if (requestLogEnabled)
                log.info("请求开始 traceId={} method={} path={}", trace, request.getMethod(), path);
            chain.doFilter(json ? wrapped : request, response);
            completed = true;
        } finally {
            try {
                long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                int httpStatus = completed ? response.getStatus() : 500;
                String params = sanitizer.value(request.getParameterMap());
                String body =
                        json
                                ? sanitizer.json(
                                        new String(
                                                wrapped.getContentAsByteArray(),
                                                StandardCharsets.UTF_8))
                                : request.getContentType() == null ? "" : "[非 JSON 正文，不记录文件及二进制内容]";
                if (requestLogEnabled)
                    log.info(
                            "请求完成 traceId={} method={} path={} status={} durationMs={} params={}"
                                    + " requestBody={} responseBody={}",
                            trace,
                            request.getMethod(),
                            path,
                            httpStatus,
                            duration,
                            params,
                            body,
                            audit.getResponseBody());
                if (httpStatus >= 400)
                    log.warn(
                            "请求失败 traceId={} method={} path={} status={} error={} durationMs={}",
                            trace,
                            request.getMethod(),
                            path,
                            httpStatus,
                            audit.getErrorMessage(),
                            duration);
                if (audit.getAction() != null) {
                    // HTTP 结束后才发布，校验失败和权限失败也包含最终响应状态。
                    try {
                        events.publishEvent(
                                new AuditEvent(
                                        audit.getUserId(),
                                        audit.getAction(),
                                        audit.getOperationType().name(),
                                        httpStatus < 400
                                                && (audit.getResponseCode() == null
                                                        || audit.getResponseCode() == 0),
                                        trace,
                                        Instant.now(),
                                        request.getMethod(),
                                        path,
                                        params,
                                        body,
                                        audit.getResponseBody(),
                                        httpStatus,
                                        audit.getResponseCode(),
                                        duration,
                                        audit.getErrorMessage()));
                    } catch (RuntimeException ex) {
                        // 审计存储异常单独告警，已完成的业务响应不能被日志故障覆盖。
                        log.error("操作审计写入失败 traceId={} action={}", trace, audit.getAction(), ex);
                    }
                }
            } finally {
                RequestAuditContext.clear();
                RequestContext.clear();
                MDC.remove("traceId");
            }
        }
    }
}
