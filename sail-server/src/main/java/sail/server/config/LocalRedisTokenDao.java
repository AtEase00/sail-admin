package sail.server.config;

import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/** 用原子 Lua 替代 Redis 6 才支持的 KEEPTTL，供本地 Redis 3 保留会话剩余有效期。 */
public class LocalRedisTokenDao extends SaTokenDaoForRedisTemplate {
    private final String prefix;
    private final RedisScript<Long> updateKeepingTtl =
            RedisScript.of(new ClassPathResource("redis/local-token-update.lua"), Long.class);

    public LocalRedisTokenDao(String namespace) {
        // 与已有 Redis 中其他项目的 Sa-Token 会话分离，禁止使用共享默认前缀。
        this.prefix = namespace + ":auth:";
    }

    @Override
    public String wrapKey(String key) {
        // 父类读、写、搜索及删除均经过此入口，确保使用同一实际键名。
        return prefix + key;
    }

    @Override
    public List<String> searchData(
            String keyPrefix, String keyword, int start, int size, boolean sortType) {
        // 父类 SCAN 返回物理键；还原逻辑键，后续 getSession 再经过 wrapKey 时才不会重复前缀。
        return super.searchData(keyPrefix, keyword, start, size, sortType).stream()
                .map(key -> key.substring(prefix.length()))
                .toList();
    }

    @Override
    public void setStringAndKeepTTL(String finalKey, String value) {
        // 键不存在时不创建；读 TTL 与更新在同一脚本内执行，避免过期会话被复活。
        stringRedisTemplate.execute(updateKeepingTtl, List.of(finalKey), value);
    }
}
