package sail.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.message.entity.UserMessage;

/** UserMessage 持久化访问。 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {}
