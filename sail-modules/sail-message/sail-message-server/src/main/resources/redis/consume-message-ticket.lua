-- 原子读取并删除一次性票据，兼容本地 Redis 3 与生产 Redis 7。
local ticket = redis.call('GET', KEYS[1])
if ticket then
    redis.call('DEL', KEYS[1])
end
return ticket
