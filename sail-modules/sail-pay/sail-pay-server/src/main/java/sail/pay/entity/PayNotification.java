package sail.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** PayNotification 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_notification")
public class PayNotification extends BaseEntity {
    private Long orderId;
    private String businessNo;
    private String eventType;
    private String status;
    private Integer attempts;
    private java.time.LocalDateTime nextAttemptAt;
    private String lastError;
}
