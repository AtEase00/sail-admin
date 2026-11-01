package sail.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** PayOrder 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_order")
public class PayOrder extends BaseEntity {
    private Long applicationId;
    private Long channelId;
    private Long ownerId;
    private String businessNo;
    private String orderNo;
    private String subject;
    private Long amount;
    private String currency;
    private String status;
    private String qrCode;
    private String channelTradeNo;
    private java.time.LocalDateTime expiresAt;
    private java.time.LocalDateTime paidAt;
    private String lastError;
}
