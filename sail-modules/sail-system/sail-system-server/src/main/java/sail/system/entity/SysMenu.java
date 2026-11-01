package sail.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** SysMenu 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {
    @com.baomidou.mybatisplus.annotation.TableField(
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long parentId;

    private String name;

    @com.baomidou.mybatisplus.annotation.TableField(
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private String icon;

    private String permission;
    private String path;
    private String type;
    private Integer sortOrder;
    private Integer status;
}
