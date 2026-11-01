package sail.pay.service;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import sail.pay.entity.*;

import java.util.Map;

/** 两个已支持渠道的显式路由，不在渠道失败时切换商户。 */
@Service
@RequiredArgsConstructor
public class ChannelGatewayImpl implements ChannelGateway {
    private final WechatGateway wechat;
    private final AlipayGateway alipay;

    private boolean wechat(PayChannel c) {
        /* 配置类型在写入时经过枚举约束。 */
        return sail.pay.enums.ChannelType.WECHAT_NATIVE.name().equals(c.getType());
    }

    @Override
    public String create(PayChannel c, PayOrder o) {
        /* 下单保持原渠道。 */
        return wechat(c) ? wechat.create(c, o) : alipay.create(c, o);
    }

    @Override
    public Trade query(PayChannel c, PayOrder o) {
        /* 返回经过 SDK 验签的事实。 */
        return wechat(c) ? wechat.query(c, o) : alipay.query(c, o);
    }

    @Override
    public void close(PayChannel c, PayOrder o) {
        /* 关单操作不直接伪造本地成功。 */
        if (wechat(c)) wechat.close(c, o);
        else alipay.close(c, o);
    }

    @Override
    public Refund refund(PayChannel c, PayOrder o, PayRefund r) {
        /* 使用稳定退款号保障渠道幂等。 */
        return wechat(c) ? wechat.refund(c, o, r) : alipay.refund(c, o, r);
    }

    @Override
    public Refund queryRefund(PayChannel c, PayOrder o, PayRefund r) {
        /* 查单失败保持处理中状态。 */
        return wechat(c) ? wechat.queryRefund(c, r) : alipay.queryRefund(c, o, r);
    }

    @Override
    public String verifyWechat(PayChannel c, HttpServletRequest request, String body) {
        /* 微信通知不得用支付宝配置验签。 */
        if (!wechat(c)) throw new sail.common.core.BusinessException(400, "渠道类型不匹配");
        return wechat.verify(c, request, body);
    }

    @Override
    public String verifyAlipay(PayChannel c, Map<String, String> params) {
        /* 支付宝通知不得用微信配置验签。 */
        if (wechat(c)) throw new sail.common.core.BusinessException(400, "渠道类型不匹配");
        return alipay.verify(c, params);
    }
}
