package sail.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** 字典类型定义，稳定编码与具体字典数据分开管理。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dictionary_type")
public class SysDictionaryType extends BaseEntity {
    private String code;
    private String name;
    private Integer status;
    private Boolean builtIn;
    private String remark;
}
