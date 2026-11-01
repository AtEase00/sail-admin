package sail.common.core;

/** 统一 HTTP 响应，业务接口返回数据及可定位请求的追踪号。 */
public record ApiResult<T>(int code, String message, T data, String traceId) {
    public static <T> ApiResult<T> ok(T data) {
        // 追踪号由请求过滤器放入线程上下文。
        return new ApiResult<>(0, "成功", data, RequestContext.traceId());
    }

    public static ApiResult<Void> error(int code, String message) {
        // 错误响应不携带内部异常或敏感数据。
        return new ApiResult<>(code, message, null, RequestContext.traceId());
    }
}
