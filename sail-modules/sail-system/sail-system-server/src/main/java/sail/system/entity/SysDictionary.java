package sail.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** SysDictionary 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dictionary")
public class SysDictionary extends BaseEntity {
    private String typeCode;
    private String label;
    private String value;
    private Integer sortOrder;
    private Integer status;
    private String color;
    private String remark;
    private Boolean isDefault;
    private Boolean builtIn;
}
