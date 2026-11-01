package sail.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.system.entity.SysRole;

/** SysRole 持久化访问。 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {}
