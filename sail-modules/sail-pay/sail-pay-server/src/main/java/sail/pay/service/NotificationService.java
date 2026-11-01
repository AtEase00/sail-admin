package sail.pay.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import sail.pay.api.PaymentEventHandler;
import sail.pay.entity.*;
import sail.pay.mapper.*;

import java.time.LocalDateTime;
import java.util.List;

/** 业务通知重试服务，没有匹配处理器时保持 WAITING 并在记录中说明。 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final PayNotificationMapper notifications;
    private final PayOrderMapper orders;
    private final PayConfigurationService config;
    private final List<PaymentEventHandler> handlers;

    public sail.common.core.PageResult<PayNotification> page(sail.common.core.ListQuery query) {
        // 分页及检索条件共用请求模型，权限范围仍由当前会话决定。
        long page = query.getPage(), size = query.getSize();
        String keyword = query.getKeyword();
        var range = query.createdRange();
        // 通知包含独立状态与下次重试时间，供管理接口诊断。
        var p =
                notifications.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                                page, size),
                        Wrappers.<PayNotification>lambdaQuery()
                        .ge(range.from() != null, PayNotification::getCreatedAt, range.from())
                        .le(range.to() != null, PayNotification::getCreatedAt, range.to())
                        .and(cn.hutool.core.util.StrUtil.isNotBlank(keyword), condition -> condition
                                .like(PayNotification::getBusinessNo, keyword))
                        .eq(cn.hutool.core.util.StrUtil.isNotBlank(query.getStatus()), PayNotification::getStatus, query.getStatus())
                                .orderByDesc(PayNotification::getId));
        return new sail.common.core.PageResult<>(p.getTotal(), page, size, p.getRecords());
    }

    public void deliver(PayNotification n) {
        // 同一订单先投递较早事件，支付通知失败时不能提前投递退款事件。
        if (notifications.selectCount(
                        Wrappers.<PayNotification>lambdaQuery()
                                .eq(PayNotification::getOrderId, n.getOrderId())
                                .lt(PayNotification::getId, n.getId())
                                .ne(PayNotification::getStatus, "DELIVERED"))
                > 0) {
            // 延后尚需等待的后继事件，避免其一直占用补偿批次影响其他订单。
            n.setLastError("等待同一订单的前序事件投递完成");
            n.setNextAttemptAt(LocalDateTime.now().plusMinutes(5));
            notifications.updateById(n);
            return;
        }
        PayOrder order = orders.selectById(n.getOrderId());
        String code = config.app(order.getApplicationId()).getCode();
        var matches = handlers.stream().filter(h -> code.equals(h.applicationCode())).toList();
        if (matches.size() != 1) {
            // 未配置或重复配置处理器都不能伪装成业务已确认。
            n.setLastError(matches.isEmpty() ? "未配置此应用的业务事件处理器" : "此应用存在多个业务事件处理器");
            n.setNextAttemptAt(LocalDateTime.now().plusMinutes(5));
            notifications.updateById(n);
            return;
        }
        try {
            // 处理器根据稳定事件号幂等执行，崩溃重试允许再次投递。
            matches.getFirst()
                    .handle(
                            new PaymentEventHandler.PaymentEvent(
                                    n.getId(),
                                    code,
                                    order.getBusinessNo(),
                                    n.getEventType(),
                                    order.getOrderNo(),
                                    order.getAmount(),
                                    order.getCurrency()));
            n.setStatus("DELIVERED");
            n.setLastError(null);
        } catch (RuntimeException ex) {
            // 失败原因只保留异常类型，不写入可能包含秘密的原始异常信息。
            n.setLastError(ex.getClass().getSimpleName());
            n.setNextAttemptAt(
                    LocalDateTime.now()
                            .plusSeconds(
                                    Math.min(3600, 30L * (1L << Math.min(7, n.getAttempts())))));
        }
        n.setAttempts(n.getAttempts() + 1);
        notifications.updateById(n);
    }
}
