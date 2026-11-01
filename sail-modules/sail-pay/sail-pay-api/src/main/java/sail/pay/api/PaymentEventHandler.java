package sail.pay.api;

/** 业务模块实现此接口接收支付事实，必须按 eventId 幂等处理。 */
public interface PaymentEventHandler {
    /** 返回此处理器唯一对应的支付应用编码。 */
    String applicationCode();

    /** 在业务事务内按事件号幂等消费，失败抛出异常交由补偿重试。 */
    void handle(PaymentEvent event);

    /** 可跨模块传递的支付事件，不包含数据库实体和渠道秘密。 */
    record PaymentEvent(
            long eventId,
            String applicationCode,
            String businessNo,
            String eventType,
            String orderNo,
            long amount,
            String currency) {}
}
