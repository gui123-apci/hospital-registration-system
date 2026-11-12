-- 库存回补脚本：key 存在才 INCR，不存在不创建。
-- 直接用 INCR 回补的坑：INCR 对不存在的 key 从 0 开始加，凭空造出库存 1，
-- 且造出来之后 setIfAbsent 会拒绝正确值回填，错误无法自愈。
-- KEYS[1] 库存 key；返回：回补后的库存（>=0），-1 表示 key 不存在（放弃回补）。
if redis.call('EXISTS', KEYS[1]) == 1 then
    return redis.call('INCR', KEYS[1])
end
return -1
