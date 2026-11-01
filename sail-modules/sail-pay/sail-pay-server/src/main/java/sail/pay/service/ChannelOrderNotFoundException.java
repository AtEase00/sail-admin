package sail.pay.service;

import sail.common.core.BusinessException;

/** 渠道明确返回交易不存在，与网络失败、验签失败及未知状态严格区分。 */
public class ChannelOrderNotFoundException extends BusinessException {
    public ChannelOrderNotFoundException() {
        // 仅在官方渠道返回明确的交易不存在错误码时创建。
        super(409, "渠道尚无此交易，未过期订单可以使用原单号重新下单");
    }
}
