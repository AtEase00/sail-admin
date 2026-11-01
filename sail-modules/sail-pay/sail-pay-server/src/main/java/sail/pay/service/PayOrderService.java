package sail.pay.service;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import sail.common.core.*;
import sail.pay.dto.PayRequests;
import sail.pay.entity.*;
import sail.pay.mapper.*;

import java.time.LocalDateTime;
import java.util.*;

/** 支付订单与整单退款，稳定业务编号和数据库唯一约束保障幂等。 */
@Service
@RequiredArgsConstructor
public class PayOrderService {
    private final PayOrderMapper orders;
    private final PayRefundMapper refunds;
    private final PayConfigurationService config;
    private final ChannelGateway gateway;
    private final PayStateService states;

    public PayOrder create(PayRequests.Order input) {
        var app = config.app(input.applicationId());
        var channel = config.get(input.channelId());
        if (!Boolean.TRUE.equals(app.getEnabled())
                || !Boolean.TRUE.equals(channel.getEnabled())
                || !channel.getApplicationId().equals(app.getId()))
            throw new BusinessException("支付应用或渠道不可用");
        PayOrder existing =
                orders.selectOne(
                        Wrappers.<PayOrder>lambdaQuery()
                                .eq(PayOrder::getApplicationId, input.applicationId())
                                .eq(PayOrder::getBusinessNo, input.businessNo()));
        if (existing != null) return checkIdempotent(existing, input);
        PayOrder order = new PayOrder();
        order.setApplicationId(input.applicationId());
        order.setChannelId(input.channelId());
        order.setOwnerId(StpUtil.getLoginIdAsLong());
        order.setBusinessNo(input.businessNo());
        // 微信商户订单号最多 32 位，统一使用 32 位随机十六进制单号。
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setSubject(input.subject());
        order.setAmount(input.amount());
        order.setCurrency("CNY");
        order.setStatus(sail.pay.enums.PayStatus.PENDING.name());
        order.setExpiresAt(LocalDateTime.now().plusMinutes(input.expiresMinutes()));
        try {
            orders.insert(order);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 并发创建同一业务单号只返回已提交的原订单。
            PayOrder winner =
                    orders.selectOne(
                            Wrappers.<PayOrder>lambdaQuery()
                                    .eq(PayOrder::getApplicationId, input.applicationId())
                                    .eq(PayOrder::getBusinessNo, input.businessNo()));
            if (winner == null) throw ex;
            return checkIdempotent(winner, input);
        }
        // 先保存订单再调用渠道，网络超时仍能通过稳定单号主动查单。
        String qr = gateway.create(channel, order);
        orders.update(
                null,
                Wrappers.<PayOrder>lambdaUpdate()
                        .eq(PayOrder::getId, order.getId())
                        .set(PayOrder::getQrCode, qr));
        order.setQrCode(qr);
        return order;
    }

    public PayOrder get(long id) {
        PayOrder order = orders.selectById(id);
        if (order == null) throw new BusinessException(404, "支付订单不存在");
        // 订单创建要求操作权限，普通查询仍校验归属。
        if (order.getOwnerId() != StpUtil.getLoginIdAsLong()
                && !StpUtil.hasPermission("pay:manage"))
            throw new BusinessException(403, "无权访问支付订单");
        return order;
    }

    public PageResult<PayOrder> page(sail.common.core.ListQuery query) {
        // 分页及检索条件共用请求模型，权限范围仍由当前会话决定。
        long page = query.getPage(), size = query.getSize();
        String keyword = query.getKeyword();
        var range = query.createdRange();
        var q = Wrappers.<PayOrder>lambdaQuery()
                        .ge(range.from() != null, PayOrder::getCreatedAt, range.from())
                        .le(range.to() != null, PayOrder::getCreatedAt, range.to())
                        .and(cn.hutool.core.util.StrUtil.isNotBlank(keyword), condition -> condition
                                .like(PayOrder::getBusinessNo, keyword).or().like(PayOrder::getOrderNo, keyword).or().like(PayOrder::getSubject, keyword))
                        .eq(cn.hutool.core.util.StrUtil.isNotBlank(query.getStatus()), PayOrder::getStatus, query.getStatus()).orderByDesc(PayOrder::getId);
        if (!StpUtil.hasPermission("pay:manage"))
            q.eq(PayOrder::getOwnerId, StpUtil.getLoginIdAsLong());
        // 分页受控且不暴露渠道密钥。
        var p = orders.selectPage(new Page<>(page, size), q);
        return new PageResult<>(p.getTotal(), page, size, p.getRecords());
    }

    public PageResult<PayRefund> refunds(sail.common.core.ListQuery query) {
        // 分页及检索条件共用请求模型，权限范围仍由当前会话决定。
        long page = query.getPage(), size = query.getSize();
        String keyword = query.getKeyword();
        var range = query.createdRange();
        // 退款管理查询统一通过服务层返回分页结果。
        var p =
                refunds.selectPage(
                        new Page<>(page, size),
                        Wrappers.<PayRefund>lambdaQuery()
                        .ge(range.from() != null, PayRefund::getCreatedAt, range.from())
                        .le(range.to() != null, PayRefund::getCreatedAt, range.to())
                        .and(cn.hutool.core.util.StrUtil.isNotBlank(keyword), condition -> condition
                                .like(PayRefund::getRefundNo, keyword).or().like(PayRefund::getReason, keyword))
                        .eq(cn.hutool.core.util.StrUtil.isNotBlank(query.getStatus()), PayRefund::getStatus, query.getStatus()).orderByDesc(PayRefund::getId));
        return new PageResult<>(p.getTotal(), page, size, p.getRecords());
    }

    public PayOrder sync(PayOrder order) {
        // 主动查单与回调使用完全相同的身份和金额校验。
        PayChannel channel = config.get(order.getChannelId());
        states.trade(order, channel, gateway.query(channel, order));
        return orders.selectById(order.getId());
    }

    public PayOrder resubmit(long id) {
        PayOrder order = get(id);
        if (!sail.pay.enums.PayStatus.PENDING.name().equals(order.getStatus())
                || order.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new BusinessException("订单状态不允许重新获取二维码");
        // 复用同一渠道单号，不为重试创建第二笔交易。
        String qr = gateway.create(config.get(order.getChannelId()), order);
        orders.update(
                null,
                Wrappers.<PayOrder>lambdaUpdate()
                        .eq(PayOrder::getId, id)
                        .eq(PayOrder::getStatus, sail.pay.enums.PayStatus.PENDING.name())
                        .set(PayOrder::getQrCode, qr));
        return orders.selectById(id);
    }

    public void close(PayOrder order) {
        if (!sail.pay.enums.PayStatus.PENDING.name().equals(order.getStatus())) return;
        // 先同步以发现支付与关单竞争，关单后再次查询，不盲目修改本地状态。
        try {
            order = sync(order);
        } catch (ChannelOrderNotFoundException ex) {
            // 仅在原定到期时间之后，依据渠道明确不存在的事实关闭本地单据。
            // 未到期不关闭，避免与正在发出的预下单请求竞争。
            if (order.getExpiresAt().isAfter(LocalDateTime.now())) throw ex;
            states.closeAbsent(order);
            return;
        }
        if (!sail.pay.enums.PayStatus.PENDING.name().equals(order.getStatus())) return;
        gateway.close(config.get(order.getChannelId()), order);
        sync(order);
    }

    public PayRefund refund(long id, String reason) {
        PayOrder order = get(id);
        if (!java.util.Set.of(
                        sail.pay.enums.PayStatus.PAID.name(),
                        sail.pay.enums.PayStatus.REFUNDED.name())
                .contains(order.getStatus())) throw new BusinessException("仅已支付订单支持整单退款");
        PayRefund refund =
                refunds.selectOne(Wrappers.<PayRefund>lambdaQuery().eq(PayRefund::getOrderId, id));
        if (refund == null) {
            refund = new PayRefund();
            refund.setOrderId(id);
            refund.setRefundNo("R" + UUID.randomUUID().toString().replace("-", ""));
            refund.setReason(reason);
            refund.setAmount(order.getAmount());
            refund.setStatus("PROCESSING");
            try {
                refunds.insert(refund);
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                // 整单退款同一订单只有一个记录，重试复用数据库胜出记录。
                refund =
                        refunds.selectOne(
                                Wrappers.<PayRefund>lambdaQuery().eq(PayRefund::getOrderId, id));
                if (refund == null) throw ex;
            }
        }
        // 数据库对 orderId 唯一，渠道始终接收同一个退款号。
        if ("PROCESSING".equals(refund.getStatus()))
            states.refund(
                    order, refund, gateway.refund(config.get(order.getChannelId()), order, refund));
        return refunds.selectById(refund.getId());
    }

    public void callback(long channelId, String orderNo) {
        PayOrder order =
                orders.selectOne(
                        Wrappers.<PayOrder>lambdaQuery()
                                .eq(PayOrder::getOrderNo, orderNo)
                                .eq(PayOrder::getChannelId, channelId));
        if (order == null) throw new BusinessException(404, "渠道订单不存在");
        // 已验签通知仅作为触发信号，再查单核实最终金额和状态。
        sync(order);
    }

    private PayOrder checkIdempotent(PayOrder order, PayRequests.Order input) {
        // 请求与原交易不一致时明确冲突，不把另一个订单当作本次创建结果。
        if (!order.getAmount().equals(input.amount())
                || !order.getChannelId().equals(input.channelId())
                || order.getOwnerId() != StpUtil.getLoginIdAsLong()
                || !order.getSubject().equals(input.subject()))
            throw new BusinessException(409, "业务单号已存在且参数不一致");
        return order;
    }
}
