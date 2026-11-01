package sail.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/** 本地旧版 Redis 适配，仅在 local 环境显式开启，不改变生产 Redis 基线。 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
@ConditionalOnProperty(name = "sail.redis.legacy-compatible", havingValue = "true")
public class LocalRedisConfiguration {
    @Bean
    @Primary
    public LocalRedisTokenDao localRedisTokenDao(
            @Value("${sail.redis.namespace}") String namespace) {
        // 使用独立命名空间隔离本工程会话，更新 TTL 的原子语义保持不变。
        return new LocalRedisTokenDao(namespace);
    }
}
