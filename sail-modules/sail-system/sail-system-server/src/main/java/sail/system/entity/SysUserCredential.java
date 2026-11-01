package sail.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import sail.framework.mybatis.BaseEntity;

/** 用户密码凭据，仅供认证服务访问，与用户资料表分开保存。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_credential")
public class SysUserCredential extends BaseEntity {
    private Long userId;

    /** 标准 BCrypt 完整编码，不保存明文或可逆密码。 */
    @JsonIgnore @ToString.Exclude private String passwordHash;

    /** 从 BCrypt 编码提取的参数和随机盐前缀，不再额外拼接到原始口令。 */
    @JsonIgnore @ToString.Exclude private String passwordSalt;
}
