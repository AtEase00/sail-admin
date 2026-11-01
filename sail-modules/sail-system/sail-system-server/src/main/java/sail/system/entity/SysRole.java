package sail.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** SysRole 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {
    private String name;
    private String code;
    private Integer status;
    private Integer dataScope;
    private Boolean builtIn;
    /** 角色用途与补充说明。 */
    private String remark;
}
