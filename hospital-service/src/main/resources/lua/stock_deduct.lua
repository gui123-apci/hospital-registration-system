-- 库存扣减脚本：判断 + 扣减在同一脚本内，Redis 单线程执行到底，中间无并发缝隙。
-- KEYS[1] 库存 key；返回：剩余库存（>=0），-1 表示库存不足。
local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
if stock <= 0 then
    return -1
end
redis.call('DECR', KEYS[1])
return stock - 1
