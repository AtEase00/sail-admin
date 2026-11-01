package sail.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.system.entity.SysMenu;

/** SysMenu 持久化访问。 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {}
