package sail.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.file.entity.StoredFile;

/** StoredFile 持久化访问。 */
@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFile> {}
