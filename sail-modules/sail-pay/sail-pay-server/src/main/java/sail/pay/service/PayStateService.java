package sail.pay.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.BusinessException;
import sail.pay.entity.*;
import sail.pay.mapper.*;

import java.time.LocalDateTime;
import java.util.*;

/** 支付及退款状态的原子推进，通知记录与状态更新位于同一事务。 */
@Service
@RequiredArgsConstructor
public class PayStateService {
    private final PayOrderMapper orders;
    private final PayRefundMapper refunds;
    private final PayNotificationMapper notifications;

    @Transactional
    public void trade(PayOrder order, PayChannel channel, ChannelGateway.Trade t) {
        // 状态推进前逐项核对，任何差异都不能拿其他字段替代。
        if (!order.getOrderNo().equals(t.orderNo())
                || order.getAmount() != t.amount()
                || !order.getCurrency().equals(t.currency())
                || (sail.pay.enums.ChannelType.WECHAT_NATIVE.name().equals(channel.getType())
                        && (!channel.getMerchantId().equals(t.merchantId())
                                || !channel.getAppId().equals(t.appId()))))
            throw new BusinessException(409, "渠道交易身份、金额或币种不匹配");
        boolean paid = Set.of("SUCCESS", "TRADE_SUCCESS", "TRADE_FINISHED").contains(t.state());
        boolean closed =
                Set.of(sail.pay.enums.PayStatus.CLOSED.name(), "REVOKED", "TRADE_CLOSED")
                        .contains(t.state());
        if (!paid && !closed) {
            // 成功查到未支付订单也更新时间，避免前 50 笔长期占用补偿批次。
            orders.update(
                    null,
                    Wrappers.<PayOrder>lambdaUpdate()
                            .eq(PayOrder::getId, order.getId())
                            .eq(PayOrder::getStatus, sail.pay.enums.PayStatus.PENDING.name())
                            .set(PayOrder::getUpdatedAt, LocalDateTime.now())
                            .set(PayOrder::getLastError, null));
            return;
        }
        if (paid && cn.hutool.core.util.StrUtil.isBlank(t.tradeNo()))
            throw new BusinessException(409, "支付成功但渠道流水号缺失");
        // 条件更新只允许 PENDING 前进，重复或乱序通知不会重复生成成功事件。
        var update =
                Wrappers.<PayOrder>lambdaUpdate()
                        .eq(PayOrder::getId, order.getId())
                        .eq(PayOrder::getStatus, sail.pay.enums.PayStatus.PENDING.name())
                        .set(
                                PayOrder::getStatus,
                                paid
                                        ? sail.pay.enums.PayStatus.PAID.name()
                                        : sail.pay.enums.PayStatus.CLOSED.name())
                        .set(PayOrder::getLastError, null)
                        .set(PayOrder::getUpdatedAt, LocalDateTime.now());
        if (paid)
            update.set(PayOrder::getChannelTradeNo, t.tradeNo())
                    .set(PayOrder::getPaidAt, LocalDateTime.now());
        if (orders.update(null, update) > 0 && paid)
            notification(order, sail.pay.enums.PayStatus.PAID.name());
    }

    @Transactional
    public void refund(PayOrder order, PayRefund refund, ChannelGateway.Refund fact) {
        if (!refund.getRefundNo().equals(fact.refundNo()) || refund.getAmount() != fact.amount())
            throw new BusinessException(409, "退款编号或金额不匹配");
        // 只有明确退款成功才推进本地状态，处理中与未知状态保持原记录。
        if (!"SUCCESS".equals(fact.state())) return;
        if (refunds.update(
                        null,
                        Wrappers.<PayRefund>lambdaUpdate()
                                .eq(PayRefund::getId, refund.getId())
                                .eq(PayRefund::getStatus, "PROCESSING")
                                .set(PayRefund::getStatus, "SUCCESS")
                                .set(PayRefund::getChannelRefundNo, fact.refundId())
                                .set(PayRefund::getLastError, null))
                == 0) return;
        if (orders.update(
                        null,
                        Wrappers.<PayOrder>lambdaUpdate()
                                .eq(PayOrder::getId, order.getId())
                                .eq(PayOrder::getStatus, sail.pay.enums.PayStatus.PAID.name())
                                .set(PayOrder::getStatus, sail.pay.enums.PayStatus.REFUNDED.name()))
                != 1) throw new BusinessException(409, "退款订单状态冲突");
        notification(order, sail.pay.enums.PayStatus.REFUNDED.name());
    }

    @Transactional
    public void closeAbsent(PayOrder order) {
        // 此入口只由渠道明确不存在且已经到期的补偿路径调用，不伪造交易金额或流水。
        orders.update(
                null,
                Wrappers.<PayOrder>lambdaUpdate()
                        .eq(PayOrder::getId, order.getId())
                        .eq(PayOrder::getStatus, sail.pay.enums.PayStatus.PENDING.name())
                        .le(PayOrder::getExpiresAt, LocalDateTime.now())
                        .set(PayOrder::getStatus, sail.pay.enums.PayStatus.CLOSED.name())
                        .set(PayOrder::getLastError, null)
                        .set(PayOrder::getUpdatedAt, LocalDateTime.now()));
    }

    private void notification(PayOrder order, String event) {
        // 事务持久化通知，业务处理失败后由任务按记录重试。
        PayNotification n = new PayNotification();
        n.setOrderId(order.getId());
        n.setBusinessNo(order.getBusinessNo());
        n.setEventType(event);
        n.setStatus("WAITING");
        n.setAttempts(0);
        n.setNextAttemptAt(LocalDateTime.now());
        notifications.insert(n);
    }
}
