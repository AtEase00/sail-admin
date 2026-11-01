package sail.pay.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** PayApplication 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_application")
public class PayApplication extends BaseEntity {
    private String code;
    private String name;
    private Boolean enabled;
}
