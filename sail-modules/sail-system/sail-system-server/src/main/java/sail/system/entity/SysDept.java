package sail.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** SysDept 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {
    @com.baomidou.mybatisplus.annotation.TableField(
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long parentId;

    private String name;
    private Integer sortOrder;
    private Integer status;
}
