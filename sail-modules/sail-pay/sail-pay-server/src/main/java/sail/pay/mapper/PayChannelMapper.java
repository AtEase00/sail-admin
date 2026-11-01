package sail.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.pay.entity.PayChannel;

/** PayChannel 持久化访问。 */
@Mapper
public interface PayChannelMapper extends BaseMapper<PayChannel> {}
