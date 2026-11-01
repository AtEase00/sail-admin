package sail.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.message.entity.MessageTemplate;

/** MessageTemplate 持久化访问。 */
@Mapper
public interface MessageTemplateMapper extends BaseMapper<MessageTemplate> {}
