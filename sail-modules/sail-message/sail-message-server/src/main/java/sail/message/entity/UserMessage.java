package sail.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** UserMessage 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_record")
public class UserMessage extends BaseEntity {
    private Long recipientId;
    private Long senderId;
    private String templateCode;
    private String title;
    private String content;
    private Boolean readFlag;
    private java.time.LocalDateTime readAt;
}
