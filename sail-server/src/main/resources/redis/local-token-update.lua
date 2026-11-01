-- 原子实现 SET XX KEEPTTL：不创建缺失键，不延长原有会话有效期。
local remaining = redis.call('PTTL', KEYS[1])
if remaining == -2 or remaining == 0 then
    return 0
end
if remaining == -1 then
    redis.call('SET', KEYS[1], ARGV[1])
else
    redis.call('PSETEX', KEYS[1], remaining, ARGV[1])
end
return 1
