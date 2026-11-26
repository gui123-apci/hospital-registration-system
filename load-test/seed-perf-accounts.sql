-- ============================================================
-- 补种压测账号：perf001~perf500 + 对应就诊人
--
-- 什么时候需要跑：
--   k6 报 `登录失败 perf001: {"code":2002,"msg":"用户名或密码错误"}`
--   且查库确认 tb_user 里没有 perf 开头的账号。
--
-- 为什么会缺：data.sql 只在 MySQL 数据卷【首次初始化】时执行一次，
--   之后改 data.sql 不会自动进库（要 down -v 重建才重跑）。
--   本脚本是幂等的，已经存在的账号会被 IGNORE 跳过，可反复执行。
--
-- 跑法（在项目根目录）：
--   Get-Content load-test\seed-perf-accounts.sql | docker exec -i hospital-mysql mysql -uroot -p123456
--
-- ⚠ 如果后端不是跑在 compose 里（比如本地 mvn spring-boot:run 连的是 3306 的本机 MySQL），
--   那要往【后端实际连接的那个库】里种，别种错地方。
-- ============================================================

USE hospital;

-- 500 个账号：id 1001~1500，用户名 perf001~perf500，密码统一 admin123
-- 哈希是 admin123 的 BCrypt 密文，与 data.sql / admin 账号一致（已实测可登录）
INSERT IGNORE INTO tb_user (id, username, password, phone, role)
SELECT 1000 + t.seq,
       CONCAT('perf', LPAD(t.seq, 3, '0')),
       '$2a$10$jy5H9RlP5R.pGQxaQHBwluCbzllgtV6r2sg.MtsXojJV9ljD8/Ff.',
       NULL,
       0
FROM (
    SELECT a.n + b.n * 10 + c.n * 100 + 1 AS seq
    FROM (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) c
) t;

-- 每个压测账号配一个就诊人：id = 2000 + i，user_id = 1000 + i
-- 压测脚本按 patientId = 2000 + i 推算，这个对应关系不能乱
INSERT IGNORE INTO tb_patient (id, user_id, name, id_card, relation)
SELECT 2000 + (u.id - 1000),
       u.id,
       CONCAT('压测患者', LPAD(u.id - 1000, 3, '0')),
       NULL,
       0
FROM tb_user u
WHERE u.id BETWEEN 1001 AND 1500;

-- 自检：账号数、就诊人数都应该是 500
SELECT
    (SELECT COUNT(*) FROM tb_user    WHERE username LIKE 'perf%') AS perf_user_count,
    (SELECT COUNT(*) FROM tb_patient WHERE id BETWEEN 2001 AND 2500) AS perf_patient_count;
