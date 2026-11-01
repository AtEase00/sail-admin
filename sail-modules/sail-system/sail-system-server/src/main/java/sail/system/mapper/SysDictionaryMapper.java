package sail.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.system.entity.SysDictionary;

/** SysDictionary 持久化访问。 */
@Mapper
public interface SysDictionaryMapper extends BaseMapper<SysDictionary> {}
