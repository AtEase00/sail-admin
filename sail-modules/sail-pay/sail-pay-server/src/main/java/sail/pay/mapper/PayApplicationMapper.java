package sail.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Mapper;

import sail.pay.entity.PayApplication;

/** PayApplication 持久化访问。 */
@Mapper
public interface PayApplicationMapper extends BaseMapper<PayApplication> {}
