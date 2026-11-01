package sail.framework.log;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/** 控制器操作在请求生命周期记录，包含参数校验与鉴权失败。 */
@AutoConfiguration
@Import(OperationLogInterceptor.class)
public class LogAutoConfiguration {}
