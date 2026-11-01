package sail.common.web;

/** 对外响应头常量，避免各层使用不同追踪号命名。 */
public final class RequestHeaders {
    public static final String TRACE_ID = "X-Trace-Id";

    private RequestHeaders() {
        /* 常量类禁止实例化。 */
    }
}
