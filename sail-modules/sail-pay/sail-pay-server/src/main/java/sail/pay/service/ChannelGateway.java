package sail.pay.service;

import jakarta.servlet.http.HttpServletRequest;

import sail.pay.entity.*;

import java.util.Map;

/** 渠道实际交易调用，统一返回经过验证的商户、金额与状态。 */
public interface ChannelGateway {
    /** 原始渠道交易事实。 */
    record Trade(
            String orderNo,
            String tradeNo,
            String appId,
            String merchantId,
            long amount,
            String currency,
            String state) {}

    /** 原始渠道退款事实。 */
    record Refund(String refundNo, String refundId, long amount, String state) {}

    /** 使用原商户单号创建实际二维码交易，返回渠道二维码文本。 */
    String create(PayChannel channel, PayOrder order);

    /** 查得经过渠道响应验签的真实交易，明确不存在时使用专门异常。 */
    Trade query(PayChannel channel, PayOrder order);

    /** 向原渠道提交关单，不能以网络异常推断为关单成功。 */
    void close(PayChannel channel, PayOrder order);

    /** 使用稳定退款号申请整单退款，返回已获得的渠道事实。 */
    Refund refund(PayChannel channel, PayOrder order, PayRefund refund);

    /** 查询原退款记录，不借用其他支付或退款编号替代。 */
    Refund queryRefund(PayChannel channel, PayOrder order, PayRefund refund);

    /** 对微信原始通知正文验签解密并核对商户，返回实际通知单号。 */
    String verifyWechat(PayChannel channel, HttpServletRequest request, String body);

    /** 对支付宝原始单值参数验签并核对应用和商户，返回实际通知单号。 */
    String verifyAlipay(PayChannel channel, Map<String, String> parameters);
}
