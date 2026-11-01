package sail.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.system.entity.SysUser;

/** SysUser 持久化访问。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {}
