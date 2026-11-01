package sail.pay.enums;

/** 支付状态只允许经过验签通知或主动查单推进。 */
public enum PayStatus {
    PENDING,
    PAID,
    CLOSED,
    REFUNDED
}
