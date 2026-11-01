package sail.pay.controller;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import sail.pay.service.*;

import java.util.*;

/** 支付平台通知入口，成功应答之前必须完成验签和交易事实同步。 */
@RestController
@RequestMapping("/api/pay/callback")
@RequiredArgsConstructor
public class PayCallbackController {
    private final ChannelGateway gateway;
    private final PayConfigurationService config;
    private final PayOrderService orders;

    @PostMapping("/wechat/{channelId}")
    public ResponseEntity<Void> wechat(
            @PathVariable long channelId, @RequestBody String body, HttpServletRequest request) {
        // 原始正文传给 SDK，不能反序列化再序列化后验签。
        String orderNo = gateway.verifyWechat(config.get(channelId), request, body);
        orders.callback(channelId, orderNo);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(
            value = "/alipay/{channelId}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String alipay(@PathVariable long channelId, HttpServletRequest request) {
        // 重复参数拒绝处理，避免参数合并造成验签歧义。
        Map<String, String> params = new HashMap<>();
        request.getParameterMap()
                .forEach(
                        (key, values) -> {
                            if (values.length != 1)
                                throw new sail.common.core.BusinessException(400, "支付宝通知参数重复");
                            params.put(key, values[0]);
                        });
        String orderNo = gateway.verifyAlipay(config.get(channelId), params);
        orders.callback(channelId, orderNo);
        return "success";
    }
}
