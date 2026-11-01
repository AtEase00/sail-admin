package sail.pay.service;

import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.*;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.*;
import com.wechat.pay.java.service.refund.RefundService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import sail.common.core.BusinessException;
import sail.framework.security.SecretCipher;
import sail.pay.entity.*;

import java.time.*;

/** 微信支付 v3 Native 与退款 SDK 适配，使用平台公钥模式验签。 */
@Component
@RequiredArgsConstructor
public class WechatGateway {
    private final SecretCipher secrets;

    private RSAPublicKeyConfig config(PayChannel c) {
        // 官方 SDK 负责请求签名和响应验签，秘密仅在调用时解密。
        return new RSAPublicKeyConfig.Builder()
                .merchantId(c.getMerchantId())
                .merchantSerialNumber(c.getMerchantSerial())
                .privateKey(secrets.decrypt(c.getPrivateKeyCipher()))
                .publicKey(secrets.decrypt(c.getPublicKeyCipher()))
                .publicKeyId(c.getPublicKeyId())
                .apiV3Key(secrets.decrypt(c.getApiV3KeyCipher()))
                .build();
    }

    public String create(PayChannel c, PayOrder o) {
        var request = new PrepayRequest();
        request.setAppid(c.getAppId());
        request.setMchid(c.getMerchantId());
        request.setDescription(o.getSubject());
        request.setOutTradeNo(o.getOrderNo());
        request.setTimeExpire(
                o.getExpiresAt().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString());
        request.setNotifyUrl(c.getNotifyBaseUrl() + "/api/pay/callback/wechat/" + c.getId());
        // 金额明确为人民币分，不把业务订单号替代渠道订单号。
        Amount amount = new Amount();
        amount.setTotal(Math.toIntExact(o.getAmount()));
        amount.setCurrency(o.getCurrency());
        request.setAmount(amount);
        return new NativePayService.Builder()
                .config(config(c))
                .build()
                .prepay(request)
                .getCodeUrl();
    }

    public ChannelGateway.Trade query(PayChannel c, PayOrder o) {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(c.getMerchantId());
        request.setOutTradeNo(o.getOrderNo());
        // 渠道返回的金额与身份交由状态服务再次对比数据库。
        Transaction t;
        try {
            t =
                    new NativePayService.Builder()
                            .config(config(c))
                            .build()
                            .queryOrderByOutTradeNo(request);
        } catch (com.wechat.pay.java.core.exception.ServiceException ex) {
            // 只识别微信明确的交易不存在错误，其他渠道异常原样上抛供重试。
            if ("ORDER_NOT_EXIST".equals(ex.getErrorCode()))
                throw new ChannelOrderNotFoundException();
            throw ex;
        }
        return new ChannelGateway.Trade(
                t.getOutTradeNo(),
                t.getTransactionId(),
                t.getAppid(),
                t.getMchid(),
                t.getAmount().getTotal(),
                t.getAmount().getCurrency(),
                t.getTradeState().name());
    }

    public void close(PayChannel c, PayOrder o) {
        // 关单只提交真实未支付订单，最终结果仍主动查询。
        CloseOrderRequest request = new CloseOrderRequest();
        request.setMchid(c.getMerchantId());
        request.setOutTradeNo(o.getOrderNo());
        new NativePayService.Builder().config(config(c)).build().closeOrder(request);
    }

    public ChannelGateway.Refund refund(PayChannel c, PayOrder o, PayRefund r) {
        var request = new com.wechat.pay.java.service.refund.model.CreateRequest();
        request.setOutTradeNo(o.getOrderNo());
        request.setOutRefundNo(r.getRefundNo());
        request.setReason(r.getReason());
        var amount = new com.wechat.pay.java.service.refund.model.AmountReq();
        amount.setRefund(r.getAmount());
        amount.setTotal(o.getAmount());
        amount.setCurrency(o.getCurrency());
        request.setAmount(amount);
        // 同一退款号用于所有重试，退款响应不直接推断为成功。
        var result = new RefundService.Builder().config(config(c)).build().create(request);
        return new ChannelGateway.Refund(
                result.getOutRefundNo(),
                result.getRefundId(),
                result.getAmount().getRefund(),
                result.getStatus().name());
    }

    public ChannelGateway.Refund queryRefund(PayChannel c, PayRefund r) {
        var request = new com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest();
        request.setOutRefundNo(r.getRefundNo());
        var result =
                new RefundService.Builder().config(config(c)).build().queryByOutRefundNo(request);
        return new ChannelGateway.Refund(
                result.getOutRefundNo(),
                result.getRefundId(),
                result.getAmount().getRefund(),
                result.getStatus().name());
    }

    public String verify(PayChannel c, HttpServletRequest request, String body) {
        // 先限制时间窗口，再由 SDK 验证平台签名并解密通知。
        long stamp = Long.parseLong(request.getHeader("Wechatpay-Timestamp"));
        if (Math.abs(Instant.now().getEpochSecond() - stamp) > 300)
            throw new BusinessException(400, "微信通知时间戳已过期");
        RequestParam param =
                new RequestParam.Builder()
                        .serialNumber(request.getHeader("Wechatpay-Serial"))
                        .timestamp(request.getHeader("Wechatpay-Timestamp"))
                        .nonce(request.getHeader("Wechatpay-Nonce"))
                        .signature(request.getHeader("Wechatpay-Signature"))
                        .body(body)
                        .build();
        Transaction t = new NotificationParser(config(c)).parse(param, Transaction.class);
        if (!c.getAppId().equals(t.getAppid()) || !c.getMerchantId().equals(t.getMchid()))
            throw new BusinessException(400, "微信通知商户身份不匹配");
        return t.getOutTradeNo();
    }
}
