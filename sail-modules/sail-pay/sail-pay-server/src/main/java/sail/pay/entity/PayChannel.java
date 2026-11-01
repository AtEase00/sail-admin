package sail.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** PayChannel 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_channel")
public class PayChannel extends BaseEntity {
    private Long applicationId;
    private String type;
    private String name;
    private String appId;
    private String merchantId;
    private String merchantSerial;
    private String publicKeyId;
    private String privateKeyCipher;
    private String publicKeyCipher;
    private String apiV3KeyCipher;
    private String notifyBaseUrl;
    private Boolean enabled;
}
