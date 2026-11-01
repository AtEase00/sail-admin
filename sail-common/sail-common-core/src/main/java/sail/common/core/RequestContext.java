package sail.common.core;

/** 请求追踪上下文，过滤器必须在 finally 中清理。 */
public final class RequestContext {
    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

    private RequestContext() {
        /* 工具类不允许实例化。 */
    }

    public static void set(String trace) {
        /* 绑定当前请求。 */
        TRACE.set(trace);
    }

    public static String traceId() {
        /* 返回实际追踪号，不使用业务参数替代。 */
        return TRACE.get();
    }

    public static void clear() {
        /* 防止线程池复用导致上下文泄漏。 */
        TRACE.remove();
    }
}
