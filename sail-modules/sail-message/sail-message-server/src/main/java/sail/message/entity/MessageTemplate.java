package sail.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** MessageTemplate 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_template")
public class MessageTemplate extends BaseEntity {
    private String code;
    private String name;
    private String title;
    private String content;
    private Boolean enabled;
}
