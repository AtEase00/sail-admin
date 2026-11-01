package sail.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** SysPost 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class SysPost extends BaseEntity {
    private String name;
    private String code;
    private Integer status;
}
