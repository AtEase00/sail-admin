package sail.pay.service;

import com.alipay.api.*;
import com.alipay.api.domain.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import sail.common.core.BusinessException;
import sail.framework.security.SecretCipher;
import sail.pay.entity.*;

import java.math.BigDecimal;
import java.util.Map;

/** 支付宝当面付 RSA2 公钥模式适配，金额转换保持分值精度。 */
@Component
@RequiredArgsConstructor
public class AlipayGateway {
    private final SecretCipher secrets;

    private AlipayClient client(PayChannel c) {
        // 正式环境地址固定，渠道配置不能将签名请求发往任意 URL。
        return new DefaultAlipayClient(
                "https://openapi.alipay.com/gateway.do",
                c.getAppId(),
                key(c.getPrivateKeyCipher()),
                "json",
                "UTF-8",
                key(c.getPublicKeyCipher()),
                "RSA2");
    }

    private String key(String encrypted) {
        // 只移除 PEM 头尾和空白，不替换其他密钥或忽略解析错误。
        return secrets.decrypt(encrypted).replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
    }

    private String yuan(long cents) {
        /* 使用十进制精确换算，禁止 double 金额。 */
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private long cents(String yuan) {
        /* 非法小数位直接失败，不能四舍五入掩盖差额。 */
        return new BigDecimal(yuan).movePointRight(2).longValueExact();
    }

    public String create(PayChannel c, PayOrder o) {
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        model.setOutTradeNo(o.getOrderNo());
        model.setTotalAmount(yuan(o.getAmount()));
        model.setSubject(o.getSubject()); // 使用订单固定的绝对到期时间，重试不能延长付款有效期。
        model.setTimeExpire(
                o.getExpiresAt()
                        .format(
                                java.time.format.DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH:mm:ss")));
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setBizModel(model);
        request.setNotifyUrl(c.getNotifyBaseUrl() + "/api/pay/callback/alipay/" + c.getId());
        // SDK 响应校验成功后才向调用方返回二维码文本。
        try {
            var response = client(c).execute(request);
            check(response);
            return response.getQrCode();
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝预下单调用失败", ex);
        }
    }

    public ChannelGateway.Trade query(PayChannel c, PayOrder o) {
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(o.getOrderNo());
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            var r = client(c).execute(request);
            check(r);
            // 支付宝查单响应由当前应用签名上下文验证。
            // 该接口未返回 seller_id，不使用配置值冒充渠道返回值；回调独立校验 seller_id。
            return new ChannelGateway.Trade(
                    r.getOutTradeNo(),
                    r.getTradeNo(),
                    null,
                    null,
                    cents(r.getTotalAmount()),
                    "CNY",
                    r.getTradeStatus());
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝查单失败", ex);
        }
    }

    public void close(PayChannel c, PayOrder o) {
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(o.getOrderNo());
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizModel(model);
        // 支付已成功时渠道拒绝关单，由后续主动查单恢复真实状态。
        try {
            check(client(c).execute(request));
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝关单失败", ex);
        }
    }

    public ChannelGateway.Refund refund(PayChannel c, PayOrder o, PayRefund refund) {
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(o.getOrderNo());
        model.setOutRequestNo(refund.getRefundNo());
        model.setRefundAmount(yuan(refund.getAmount()));
        model.setRefundReason(refund.getReason());
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);
        // 无论是否发生资金变动，统一再查退款号确认最终状态。
        try {
            check(client(c).execute(request));
            return queryRefund(c, o, refund);
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝退款调用失败", ex);
        }
    }

    public ChannelGateway.Refund queryRefund(PayChannel c, PayOrder o, PayRefund refund) {
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutTradeNo(o.getOrderNo());
        model.setOutRequestNo(refund.getRefundNo());
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        request.setBizModel(model);
        try {
            var r = client(c).execute(request);
            check(r); // 此接口未提供独立渠道退款流水，保留空值，不使用支付流水替代。
            if (cn.hutool.core.util.StrUtil.isBlank(r.getOutRequestNo())
                    || cn.hutool.core.util.StrUtil.isBlank(r.getRefundAmount()))
                throw new BusinessException(502, "支付宝退款查询尚未返回退款号或金额");
            return new ChannelGateway.Refund(
                    r.getOutRequestNo(),
                    null,
                    cents(r.getRefundAmount()),
                    "REFUND_SUCCESS".equals(r.getRefundStatus()) ? "SUCCESS" : "PROCESSING");
        } catch (AlipayApiException ex) {
            throw new IllegalStateException("支付宝退款查单失败", ex);
        }
    }

    public String verify(PayChannel c, Map<String, String> params) {
        // 验签与应用、商户校验均成功后才接收订单号；随后仍主动查单核实金额。
        try {
            if (!AlipaySignature.rsaCheckV1(params, key(c.getPublicKeyCipher()), "UTF-8", "RSA2"))
                throw new BusinessException(400, "支付宝通知验签失败");
        } catch (AlipayApiException ex) {
            throw new BusinessException(400, "支付宝通知签名不合法");
        }
        if (!c.getAppId().equals(params.get("app_id")))
            throw new BusinessException(400, "支付宝通知应用不匹配");
        if (c.getMerchantId() != null
                && !c.getMerchantId().isBlank()
                && !c.getMerchantId().equals(params.get("seller_id")))
            throw new BusinessException(400, "支付宝通知商户不匹配");
        return params.get("out_trade_no");
    }

    private void check(AlipayResponse response) {
        // 只对外暴露渠道业务错误码，不返回包含请求内容的 SDK 异常消息。
        if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode()))
            throw new ChannelOrderNotFoundException();
        if (!response.isSuccess())
            throw new BusinessException(502, "支付宝调用未成功：" + response.getSubCode());
    }
}
