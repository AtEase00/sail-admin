package sail.common.core;

import java.time.Instant;

/** 请求完成后的脱敏操作快照，包含最终状态和完整耗时。 */
public record AuditEvent(
        Long userId,
        String action,
        String operationType,
        boolean success,
        String traceId,
        Instant time,
        String requestMethod,
        String requestUrl,
        String requestParams,
        String requestBody,
        String responseBody,
        int httpStatus,
        Integer responseCode,
        long durationMs,
        String errorMessage) {}
