package sail.framework.mybatis;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.LocalDateTime;

/** 数据库实体公共审计字段，逻辑删除以本行主键作为删除标记。 */
@Data
public abstract class BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 未删除为零，逻辑删除由 MyBatis-Plus 在数据库表达式中写入本行主键。 */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
