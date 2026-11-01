package sail.framework.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/** Redis 缓存默认策略，连接工厂和模板交由 Spring Boot 管理。 */
@AutoConfiguration
public class RedisAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public RedisCacheConfiguration redisCacheConfiguration() {
        // 默认缓存一小时且不缓存 null，业务可按命名缓存覆盖有效期。
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues();
    }
}
