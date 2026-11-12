-- 库存扣减脚本：判断 + 扣减在同一脚本内，Redis 单线程执行到底，中间无并发缝隙。
-- KEYS[1] 库存 key；返回：剩余库存（>=0）；-1 库存不足；-2 缓存缺失（key 被删/TTL 过期/Redis 重启），
-- -2 不能与 -1 混同：库里可能明明有号，患者不能看到假的"已约满"，应走数据库兜底。
local stock = redis.call('GET', KEYS[1])
if (not stock) then
    return -2
end
stock = tonumber(stock)
if stock <= 0 then
    return -1
end
redis.call('DECR', KEYS[1])
return stock - 1
