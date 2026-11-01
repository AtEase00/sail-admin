package sail.pay.controller;

import cn.dev33.satoken.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.pay.dto.PayRequests;
import sail.pay.entity.*;
import sail.pay.service.*;

/** PC 扫码支付管理接口，金额创建与退款均要求明确操作权限。 */
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {
    private final PayConfigurationService config;
    private final PayOrderService orders;
    private final NotificationService notifications;

    @GetMapping("/channels/available")
    @SaCheckPermission("pay:create")
    public ApiResult<?> availableChannels() {
        // 具有下单权限的账号读取最小渠道目录，无需超级管理员配置权限。
        return ApiResult.ok(config.availableChannels());
    }

    @GetMapping("/applications")
    @SaCheckPermission("pay:query")
    public ApiResult<?> apps(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 应用只返回非秘密资料。 */
        return ApiResult.ok(config.apps(query));
    }

    @PostMapping("/applications")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("创建支付应用")
    public ApiResult<?> app(@Valid @RequestBody PayRequests.App v) {
        /* 配置仅限超级管理员。 */
        return ApiResult.ok(config.app(null, v));
    }

    @PutMapping("/applications/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("修改支付应用")
    public ApiResult<?> app(@PathVariable long id, @Valid @RequestBody PayRequests.App v) {
        /* 禁用不影响历史订单查单。 */
        return ApiResult.ok(config.app(id, v));
    }

    @GetMapping("/channels")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    public ApiResult<?> channels(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 密钥值不会出现在响应。 */
        return ApiResult.ok(config.channels(query));
    }

    @PostMapping("/channels")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("创建支付渠道")
    public ApiResult<?> channel(@Valid @RequestBody PayRequests.Channel v) {
        /* 接收真实商户配置并加密保存。 */
        return ApiResult.ok(config.channel(null, v));
    }

    @PutMapping("/channels/{id}")
    @SaCheckRole(sail.framework.security.SecurityConstants.SUPER_ADMIN)
    @OperationLog("修改支付渠道")
    public ApiResult<?> channel(@PathVariable long id, @Valid @RequestBody PayRequests.Channel v) {
        /* 已引用渠道禁止改变商户身份。 */
        return ApiResult.ok(config.channel(id, v));
    }

    @PostMapping("/orders")
    @SaCheckPermission("pay:create")
    @OperationLog("创建扫码支付订单")
    public ApiResult<?> create(@Valid @RequestBody PayRequests.Order v) {
        /* 业务编号幂等，金额单位为分。 */
        return ApiResult.ok(orders.create(v));
    }

    @GetMapping("/orders")
    @SaCheckPermission("pay:query")
    public ApiResult<?> page(
            @jakarta.validation.Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 普通用户只能查询本人创建的订单。 */
        return ApiResult.ok(orders.page(query));
    }

    @GetMapping("/orders/{id}")
    @SaCheckPermission("pay:query")
    public ApiResult<?> get(@PathVariable long id) {
        /* 详情校验订单归属。 */
        return ApiResult.ok(orders.get(id));
    }

    @PostMapping("/orders/{id}/sync")
    @SaCheckPermission("pay:query")
    @OperationLog(value = "查询支付渠道订单", type = sail.common.core.OperationType.QUERY)
    public ApiResult<?> sync(@PathVariable long id) {
        /* 主动查询渠道真实状态。 */
        return ApiResult.ok(orders.sync(orders.get(id)));
    }

    @PostMapping("/orders/{id}/resubmit")
    @SaCheckPermission("pay:create")
    @OperationLog(value = "重新提交支付订单", type = sail.common.core.OperationType.UPDATE)
    public ApiResult<?> resubmit(@PathVariable long id) {
        /* 使用原单号重试下单。 */
        return ApiResult.ok(orders.resubmit(id));
    }

    @PostMapping("/orders/{id}/close")
    @SaCheckPermission("pay:manage")
    @OperationLog(value = "关闭支付订单", type = sail.common.core.OperationType.UPDATE)
    public ApiResult<?> close(@PathVariable long id) {
        /* 关闭前后均查询渠道。 */
        orders.close(orders.get(id));
        return ApiResult.ok(null);
    }

    @PostMapping("/orders/{id}/refund")
    @SaCheckPermission("pay:refund")
    @OperationLog("申请整单退款")
    public ApiResult<?> refund(@PathVariable long id, @Valid @RequestBody PayRequests.Refund v) {
        /* 同一订单重复请求复用退款号。 */
        return ApiResult.ok(orders.refund(id, v.reason()));
    }

    @GetMapping("/refunds")
    @SaCheckPermission("pay:manage")
    public ApiResult<?> refunds(
            @jakarta.validation.Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 退款记录包含可诊断的处理状态。 */
        return ApiResult.ok(orders.refunds(query));
    }

    @GetMapping("/notifications")
    @SaCheckPermission("pay:manage")
    public ApiResult<?> notifications(
            @jakarta.validation.Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 未配置业务处理器的通知保持 WAITING。 */
        return ApiResult.ok(notifications.page(query));
    }
}
