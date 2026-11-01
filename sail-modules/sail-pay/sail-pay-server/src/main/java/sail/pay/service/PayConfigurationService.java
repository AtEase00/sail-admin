package sail.pay.service;

import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import sail.common.core.BusinessException;
import sail.common.core.ListQuery;
import sail.framework.mybatis.DirectoryQueries;
import sail.framework.security.SecretCipher;
import sail.pay.dto.PayRequests;
import sail.pay.entity.*;
import sail.pay.mapper.*;

import java.net.URI;
import java.util.*;

/** 支付应用与渠道配置，已被订单引用的商户身份不可改变。 */
@Service
@RequiredArgsConstructor
public class PayConfigurationService {
    private final PayApplicationMapper apps;
    private final PayChannelMapper channels;
    private final PayOrderMapper orders;
    private final SecretCipher secrets;

    public List<PayApplication> apps(ListQuery query) {
        var wrapper = DirectoryQueries.<PayApplication>from(query);
        wrapper.eq(StrUtil.isNotBlank(query.getType()), "type", query.getType());
        var status = query.booleanStatus();
        wrapper.eq(status != null, "enabled", status)
                .and(
                        StrUtil.isNotBlank(query.getKeyword()),
                        q ->
                                q.like("name", query.getKeyword())
                                        .or()
                                        .like("code", query.getKeyword()));
        /* 应用目录不包含秘密。 */
        return apps.selectList(wrapper);
    }

    public long app(Long id, PayRequests.App v) {
        PayApplication e = id == null ? new PayApplication() : apps.selectById(id);
        if (e == null) throw new BusinessException("支付应用不存在");
        // 业务应用编码稳定，通知处理器按此编码接收事件。
        if (id != null && !e.getCode().equals(v.code())) throw new BusinessException("支付应用编码不能变更");
        e.setCode(v.code());
        e.setName(v.name());
        e.setEnabled(v.enabled());
        if (id == null) apps.insert(e);
        else apps.updateById(e);
        return e.getId();
    }

    public List<Map<String, Object>> channels(ListQuery query) {
        var wrapper = DirectoryQueries.<PayChannel>from(query);
        var status = query.booleanStatus();
        wrapper.eq(status != null, "enabled", status)
                .and(
                        StrUtil.isNotBlank(query.getKeyword()),
                        q ->
                                q.like("name", query.getKeyword())
                                        .or()
                                        .like("app_id", query.getKeyword())
                                        .or()
                                        .like("merchant_id", query.getKeyword()));
        // 仅列出必要身份与秘密是否已配置，不返回加密密文。
        return channels.selectList(wrapper).stream()
                .map(
                        c -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", c.getId());
                            row.put("applicationId", c.getApplicationId());
                            row.put("name", c.getName());
                            row.put("type", c.getType());
                            row.put("appId", c.getAppId());
                            row.put("merchantId", c.getMerchantId());
                            row.put("merchantSerial", c.getMerchantSerial());
                            row.put("publicKeyId", c.getPublicKeyId());
                            row.put("notifyBaseUrl", c.getNotifyBaseUrl());
                            row.put("enabled", c.getEnabled());
                            row.put(
                                    "credentialsConfigured",
                                    c.getPrivateKeyCipher() != null
                                            && c.getPublicKeyCipher() != null);
                            return row;
                        })
                .toList();
    }

    public List<Map<String, Object>> availableChannels() {
        // 下单页面只读取启用应用下的可用渠道，不返回商户凭据或私钥配置。
        Set<Long> enabledApps =
                apps
                        .selectList(
                                Wrappers.<PayApplication>lambdaQuery()
                                        .eq(PayApplication::getEnabled, true))
                        .stream()
                        .map(PayApplication::getId)
                        .collect(java.util.stream.Collectors.toSet());
        return channels
                .selectList(
                        Wrappers.<PayChannel>lambdaQuery()
                                .eq(PayChannel::getEnabled, true)
                                .orderByAsc(PayChannel::getId))
                .stream()
                .filter(channel -> enabledApps.contains(channel.getApplicationId()))
                .map(
                        channel ->
                                Map.<String, Object>of(
                                        "id",
                                        channel.getId(),
                                        "applicationId",
                                        channel.getApplicationId(),
                                        "name",
                                        channel.getName(),
                                        "type",
                                        channel.getType()))
                .toList();
    }

    public long channel(Long id, PayRequests.Channel v) {
        PayChannel c = id == null ? new PayChannel() : get(id);
        app(v.applicationId());
        URI callback = URI.create(v.notifyBaseUrl());
        if (!"https".equals(callback.getScheme())
                || callback.getHost() == null
                || callback.getUserInfo() != null
                || callback.getQuery() != null
                || callback.getFragment() != null)
            throw new BusinessException("回调基础地址必须是无查询参数的 HTTPS URL");
        // 更换商户或应用会破坏历史订单查单，必须新增渠道配置。
        if (id != null
                && orders.selectCount(
                                Wrappers.<PayOrder>lambdaQuery().eq(PayOrder::getChannelId, id))
                        > 0
                && (!c.getApplicationId().equals(v.applicationId())
                        || !c.getType().equals(v.type().name())
                        || !c.getAppId().equals(v.appId())
                        || !Objects.equals(c.getMerchantId(), v.merchantId())))
            throw new BusinessException("渠道已有订单，商户身份变更请新建渠道");
        c.setApplicationId(v.applicationId());
        c.setType(v.type().name());
        c.setName(v.name());
        c.setAppId(v.appId());
        c.setMerchantId(v.merchantId());
        c.setMerchantSerial(v.merchantSerial());
        c.setPublicKeyId(v.publicKeyId());
        c.setNotifyBaseUrl(StrUtil.removeSuffix(v.notifyBaseUrl(), "/"));
        c.setEnabled(v.enabled());
        if (StrUtil.isNotBlank(v.privateKey()))
            c.setPrivateKeyCipher(secrets.encrypt(v.privateKey()));
        if (StrUtil.isNotBlank(v.publicKey())) c.setPublicKeyCipher(secrets.encrypt(v.publicKey()));
        if (StrUtil.isNotBlank(v.apiV3Key())) c.setApiV3KeyCipher(secrets.encrypt(v.apiV3Key()));
        if (StrUtil.isBlank(c.getMerchantId()))
            throw new BusinessException("商户号或支付宝 seller_id 必须配置");
        if (c.getPrivateKeyCipher() == null || c.getPublicKeyCipher() == null)
            throw new BusinessException("支付私钥和平台公钥必须配置");
        if (sail.pay.enums.ChannelType.WECHAT_NATIVE.name().equals(c.getType())
                && (StrUtil.hasBlank(c.getMerchantId(), c.getMerchantSerial(), c.getPublicKeyId())
                        || c.getApiV3KeyCipher() == null
                        || secrets.decrypt(c.getApiV3KeyCipher())
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                        .length
                                != 32))
            throw new BusinessException("微信商户号、证书序列号、平台公钥 ID 和 32 字节 APIv3 密钥必须完整配置");
        if (id == null) channels.insert(c);
        else channels.updateById(c);
        return c.getId();
    }

    public PayChannel get(long id) {
        /* 不存在的渠道不替换为其他渠道。 */
        PayChannel c = channels.selectById(id);
        if (c == null) throw new BusinessException("支付渠道不存在");
        return c;
    }

    public PayApplication app(long id) {
        /* 查单和退款可继续使用停用应用，停用仅阻止新订单。 */
        PayApplication a = apps.selectById(id);
        if (a == null) throw new BusinessException("支付应用不存在");
        return a;
    }
}
