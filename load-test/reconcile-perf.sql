-- ============================================================
-- 压测后对账：确认"退号回补"链路的最终状态
--
-- 跑完 load-test/refund-concurrency.js 之后执行：
--   Get-Content load-test\reconcile-perf.sql | docker exec -i hospital-mysql mysql -uroot -p123456
--
-- 判定口径（三个数字必须同时成立）：
--   库存归零、有效订单 = 20、锁定号源 = 20
-- 任何一个对不上，说明某条防线漏了：
--   库存没归零   → 库存回补或扣减有偏差
--   有效订单 ≠ 20 → 超卖或少卖
--   锁定号源 ≠ 20 → 号源状态机跳步
-- ============================================================

USE hospital;

-- ① 库存：三轮下来号已经全部被第二段重新卖出 → 期望 0
SELECT '① 剩余库存' AS item, left_num AS value FROM tb_schedule WHERE id = 9001;

-- ② 订单按状态分布：期望 已退号(2)=20、待支付(0)=20
SELECT
    status,
    CASE status
        WHEN 0 THEN '待支付'
        WHEN 1 THEN '已支付'
        WHEN 2 THEN '已退号'
        WHEN 3 THEN '已就诊'
        WHEN 4 THEN '已取消'
        WHEN 5 THEN '爽约'
        WHEN 6 THEN '就诊中'
        ELSE '未知'
    END AS status_desc,
    COUNT(*) AS cnt
FROM tb_order
WHERE schedule_id = 9001
GROUP BY status
ORDER BY status;

-- ③ 有效订单数（active_flag=1）：期望 20。
--    如果这里是 40，说明退号没把 active_flag 置 NULL，唯一键没让位
SELECT '③ 有效订单数' AS item, COUNT(*) AS value
FROM tb_order
WHERE schedule_id = 9001 AND active_flag = 1;

-- ④ 号源状态分布：期望 锁定(1)=20，可用(0)=0
SELECT
    status,
    CASE status
        WHEN 0 THEN '可用'
        WHEN 1 THEN '锁定'
        WHEN 2 THEN '已售出'
        WHEN 3 THEN '已就诊'
        ELSE '未知'
    END AS status_desc,
    COUNT(*) AS cnt
FROM tb_number_source
WHERE schedule_id = 9001
GROUP BY status
ORDER BY status;

-- ⑤ 支付单：期望 退款(3)=20
--    第一轮 20 单都付过款又退掉了，所以支付单全部是退款态；
--    第三轮重新抢到的 20 单还是"待支付"，没有支付单
SELECT
    p.status,
    CASE p.status
        WHEN 0 THEN '待支付'
        WHEN 1 THEN '成功'
        WHEN 2 THEN '失败'
        WHEN 3 THEN '退款'
        ELSE '未知'
    END AS status_desc,
    COUNT(*) AS cnt
FROM tb_payment p
JOIN tb_order o ON p.order_id = o.id
WHERE o.schedule_id = 9001
GROUP BY p.status
ORDER BY p.status;

-- ⑥ 汇总：一句话看结论（三项全为 PASS 才算链路完整）
SELECT
    CASE WHEN s.left_num = 0 THEN 'PASS' ELSE 'FAIL' END AS stock_ok,
    CASE WHEN (SELECT COUNT(*) FROM tb_order o
               WHERE o.schedule_id = 9001 AND o.active_flag = 1) = 20
         THEN 'PASS' ELSE 'FAIL' END AS order_ok,
    CASE WHEN (SELECT COUNT(*) FROM tb_number_source ns
               WHERE ns.schedule_id = 9001 AND ns.status = 1) = 20
         THEN 'PASS' ELSE 'FAIL' END AS source_ok
FROM tb_schedule s
WHERE s.id = 9001;
