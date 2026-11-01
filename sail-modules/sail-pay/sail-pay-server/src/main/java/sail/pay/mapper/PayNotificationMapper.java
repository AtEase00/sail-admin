package sail.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.pay.entity.PayNotification;

/** PayNotification 持久化访问。 */
@Mapper
public interface PayNotificationMapper extends BaseMapper<PayNotification> {}
