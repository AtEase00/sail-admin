package sail.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.system.entity.SysDictionaryType;

/** 字典类型持久化访问。 */
@Mapper
public interface SysDictionaryTypeMapper extends BaseMapper<SysDictionaryType> {}
