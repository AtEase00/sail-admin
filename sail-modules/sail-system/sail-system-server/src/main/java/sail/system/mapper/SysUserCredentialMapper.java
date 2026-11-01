package sail.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.system.entity.SysUserCredential;

/** 用户凭据持久化访问，业务列表不关联该表。 */
@Mapper
public interface SysUserCredentialMapper extends BaseMapper<SysUserCredential> {}
