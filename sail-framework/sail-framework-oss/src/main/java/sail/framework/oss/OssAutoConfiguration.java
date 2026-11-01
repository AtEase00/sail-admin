package sail.framework.oss;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/** 本地及对象存储适配按依赖自动装配，未配置云实例时不连接外部服务。 */
@AutoConfiguration
@Import({LocalObjectStore.class, CloudObjectStore.class})
public class OssAutoConfiguration {}
