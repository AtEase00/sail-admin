package sail.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** PayRefund 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_refund")
public class PayRefund extends BaseEntity {
    private Long orderId;
    private String refundNo;
    private String reason;
    private Long amount;
    private String status;
    private String channelRefundNo;
    private String lastError;
}
