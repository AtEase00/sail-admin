package sail.common.core;

import lombok.Data;

/** 单次请求的审计上下文，由过滤器创建和清理，不跨线程或请求共享。 */
@Data
public class RequestAuditContext {
    private static final ThreadLocal<RequestAuditContext> CURRENT = new ThreadLocal<>();
    private Long userId;
    private String action;
    private OperationType operationType;
    private String responseBody;
    private Integer responseCode;
    private String errorMessage;

    /** 请求开始时创建独立上下文。 */
    public static RequestAuditContext begin() {
        var context = new RequestAuditContext();
        CURRENT.set(context);
        return context;
    }

    /** 返回当前请求的真实上下文，后台线程没有请求上下文。 */
    public static RequestAuditContext current() {
        return CURRENT.get();
    }

    /** 请求完成后移除，防止线程池复用污染后续请求。 */
    public static void clear() {
        CURRENT.remove();
    }
}
