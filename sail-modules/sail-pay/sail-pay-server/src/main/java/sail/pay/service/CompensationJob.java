package sail.pay.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sail.pay.entity.*;
import sail.pay.mapper.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** 支付查单、超时关单、退款同步及通知补偿，错误记录可供运维检索。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationJob {
    private final JobLeaseMapper leases;
    private final PayOrderMapper orders;
    private final PayRefundMapper refunds;
    private final PayNotificationMapper notifications;
    private final PayOrderService service;
    private final PayConfigurationService config;
    private final ChannelGateway gateway;
    private final PayStateService states;
    private final NotificationService delivery;

    @Value("${sail.pay.compensation-enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${sail.pay.compensation-delay:60000}")
    public void execute() {
        if (!enabled) return;
        String owner = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        if (leases.acquire(owner, now, now.plusMinutes(10)) == 0) return;
        try {
            // 小批量按最近更新时间轮转，失败记录不会长期阻塞后续订单。
            for (PayOrder order :
                    orders.selectList(
                            Wrappers.<PayOrder>lambdaQuery()
                                    .eq(
                                            PayOrder::getStatus,
                                            sail.pay.enums.PayStatus.PENDING.name())
                                    .orderByAsc(PayOrder::getUpdatedAt)
                                    .last("LIMIT 50"))) {
                if (!renew(owner)) return;
                try {
                    if (order.getExpiresAt().isBefore(LocalDateTime.now())) service.close(order);
                    else service.sync(order);
                } catch (RuntimeException ex) {
                    log.error(
                            "支付补偿失败 orderId={} type={}",
                            order.getId(),
                            ex.getClass().getSimpleName());
                    orders.update(
                            null,
                            Wrappers.<PayOrder>lambdaUpdate()
                                    .eq(PayOrder::getId, order.getId())
                                    .set(PayOrder::getLastError, ex.getClass().getSimpleName())
                                    .set(PayOrder::getUpdatedAt, LocalDateTime.now()));
                }
            }
            for (PayRefund refund :
                    refunds.selectList(
                            Wrappers.<PayRefund>lambdaQuery()
                                    .eq(PayRefund::getStatus, "PROCESSING")
                                    .orderByAsc(PayRefund::getUpdatedAt)
                                    .last("LIMIT 50"))) {
                if (!renew(owner)) return;
                try {
                    PayOrder order = orders.selectById(refund.getOrderId());
                    states.refund(
                            order,
                            refund,
                            gateway.refund(config.get(order.getChannelId()), order, refund));
                    // 已受理但尚未完成的退款也轮转批次，避免持续占用最早的扫描位置。
                    refunds.update(
                            null,
                            Wrappers.<PayRefund>lambdaUpdate()
                                    .eq(PayRefund::getId, refund.getId())
                                    .set(PayRefund::getUpdatedAt, LocalDateTime.now()));
                } catch (RuntimeException ex) {
                    log.error(
                            "退款补偿失败 refundId={} type={}",
                            refund.getId(),
                            ex.getClass().getSimpleName());
                    refunds.update(
                            null,
                            Wrappers.<PayRefund>lambdaUpdate()
                                    .eq(PayRefund::getId, refund.getId())
                                    .set(PayRefund::getLastError, ex.getClass().getSimpleName())
                                    .set(PayRefund::getUpdatedAt, LocalDateTime.now()));
                }
            }
            for (PayNotification n :
                    notifications.selectList(
                            Wrappers.<PayNotification>lambdaQuery()
                                    .eq(PayNotification::getStatus, "WAITING")
                                    .le(PayNotification::getNextAttemptAt, LocalDateTime.now())
                                    .orderByAsc(PayNotification::getId)
                                    .last("LIMIT 50"))) {
                if (!renew(owner)) return;
                delivery.deliver(n);
            }
        } finally {
            leases.release(owner, LocalDateTime.now());
        }
    }

    private boolean renew(String owner) {
        /* 续约失败立即停止启动新工作，旧持有者不抢占新租约。 */
        LocalDateTime now = LocalDateTime.now();
        return leases.renew(owner, now, now.plusMinutes(10)) == 1;
    }
}
