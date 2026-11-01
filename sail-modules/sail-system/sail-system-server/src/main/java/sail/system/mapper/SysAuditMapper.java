package sail.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.system.entity.SysAudit;

/** SysAudit 持久化访问。 */
@Mapper
public interface SysAuditMapper extends BaseMapper<SysAudit> {}
