-- ============================================================
-- 压测数据重置：把排班 9001 恢复到"明天出诊、20 个号、无任何订单"
--
-- 为什么必须重置：
--   1. data.sql 里 9001 的 work_date 是"建库当天 + 1 天"，数据卷建了几天之后就过期了，
--      此时下单会被 SCHEDULE_SLOT_EXPIRED(2045) 拦，退号会被 REFUND_NOT_ALLOWED(2059) 拦；
--   2. 上一轮压测的订单、支付单、已售号源都还在，不清掉库存和状态都对不上。
--
-- 跑法（在项目根目录，PowerShell）：
--   Get-Content load-test\reset-perf-data.sql | docker exec -i hospital-mysql mysql -uroot -p123456
--   docker exec hospital-redis redis-cli DEL stock:9001
--
-- ⚠ 第二行不能省：只清库不清 Redis 的话，stock:9001 还停在 0，
--   下一轮抢号的 Lua 直接返回 -1（已约满），看起来像"库存没还回来"，实际是缓存没刷新。
-- ============================================================

USE hospital;

-- 先删支付单（有 order_id 引用关系），再删订单
DELETE p FROM tb_payment p
    JOIN tb_order o ON p.order_id = o.id
WHERE o.schedule_id = 9001;

DELETE FROM tb_order WHERE schedule_id = 9001;

-- 号源整段重建，比逐条 UPDATE 更干净（uk_schedule_source 是 schedule_id + source_no）
DELETE FROM tb_number_source WHERE schedule_id = 9001;

-- 排班回到初始态：日期刷成明天，库存、总号数、状态全部复位
UPDATE tb_schedule
SET work_date = CURDATE() + INTERVAL 1 DAY,
    total_num = 20,
    left_num  = 20,
    status    = 1
WHERE id = 9001;

-- 重新生成 20 个号源：a 产出 0~9，b 产出 0/1，拼出 1~20
INSERT INTO tb_number_source (schedule_id, source_no, status)
SELECT 9001, a.n + b.n * 10 + 1, 0
FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1) b;

-- 自检：进度条应该显示 left_num=20、号源 20 条、订单 0 条
SELECT
    s.id,
    s.work_date,
    s.left_num,
    (SELECT COUNT(*) FROM tb_number_source ns WHERE ns.schedule_id = s.id) AS source_count,
    (SELECT COUNT(*) FROM tb_order o WHERE o.schedule_id = s.id)          AS order_count
FROM tb_schedule s
WHERE s.id = 9001;
