-- ============================================================
-- 演示数据 + 压测数据
-- 由 docker-compose 挂载为 02-data.sql，在 01-schema.sql 之后执行
-- 所有测试账号密码统一为 admin123（BCrypt 密文取自 admin 账号）
-- 仅在容器数据库首次初始化时执行；想重建：docker compose down -v 再 up
-- ============================================================

-- 显式声明连接字符集，避免中文在初始化时被按 latin1 解释成乱码
SET NAMES utf8mb4;

USE hospital;

-- ---------- 1. 科室（导诊关键词兜底依赖这些名字）----------
INSERT INTO tb_department (id, name, description) VALUES
(1, '心内科', '心血管疾病诊治，高血压、冠心病'),
(2, '呼吸内科', '呼吸系统疾病诊治，咳嗽、哮喘'),
(3, '消化内科', '消化系统疾病诊治，胃痛、腹泻'),
(4, '骨科', '骨骼与关节疾病，骨折、腰腿痛'),
(5, '儿科', '儿童常见病，发热、咳嗽');

-- ---------- 2. 医生（id=6 专供压测，不参与业务演示）----------
INSERT INTO tb_doctor (id, department_id, name, title, intro) VALUES
(1, 1, '张建国', '主任医师', '擅长冠心病、高血压诊治'),
(2, 2, '李慧敏', '副主任医师', '擅长慢阻肺、哮喘诊治'),
(3, 3, '王强',   '主治医师', '擅长胃炎、消化性溃疡诊治'),
(4, 4, '赵宏伟', '主任医师', '擅长骨折与关节置换'),
(5, 5, '陈晓燕', '副主任医师', '擅长儿童呼吸道感染'),
(6, 1, '压测专用医生', '主治医师', '仅用于并发压测，请勿挂这个号做演示');

-- ---------- 3. 排班：明天，每个 20 个号 ----------
-- 前 5 条用于演示，id=9001 是压测专用排班（下午时段，避开演示排班）
INSERT INTO tb_schedule (id, doctor_id, department_id, work_date, time_slot, total_num, left_num, price, status) VALUES
(1, 1, 1, CURDATE() + INTERVAL 1 DAY, 1, 20, 20, 50.00, 1),
(2, 2, 2, CURDATE() + INTERVAL 1 DAY, 1, 20, 20, 50.00, 1),
(3, 3, 3, CURDATE() + INTERVAL 1 DAY, 1, 20, 20, 50.00, 1),
(4, 4, 4, CURDATE() + INTERVAL 1 DAY, 1, 20, 20, 50.00, 1),
(5, 5, 5, CURDATE() + INTERVAL 1 DAY, 1, 20, 20, 50.00, 1),
(9001, 6, 1, CURDATE() + INTERVAL 1 DAY, 2, 20, 20, 50.00, 1);

-- ---------- 4. 号源：每个排班生成 20 条（source_no 1~20，状态 0 可用）----------
INSERT INTO tb_number_source (schedule_id, source_no, status)
SELECT s.id, d.n + 1, 0
FROM tb_schedule s
CROSS JOIN (
    SELECT a.n + b.n * 10 AS n
    FROM (SELECT 0 n UNION SELECT 1) a
    CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
) d;

-- ---------- 5. 压测账号 500 个：id 1001~1500，用户名 perf001~perf500 ----------
INSERT INTO tb_user (id, username, password, phone, role)
SELECT 1000 + t.seq,
       CONCAT('perf', LPAD(t.seq, 3, '0')),
       '$2a$10$KQgWQUGKwZpPRk2ZGlStreknAczP6xGG2hz0y7bBC1d4D5JCF.LKC',
       NULL,
       0
FROM (
    SELECT a.n + b.n * 10 + c.n * 100 + 1 AS seq
    FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
    CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
    CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) c
) t;

-- ---------- 6. 每个压测账号配一个就诊人：id 2001~2500（与账号一一对应）----------
-- 约定：账号 id = 1000 + i，则就诊人 id = 2000 + i（压测脚本按此推算，勿打乱顺序）
INSERT INTO tb_patient (id, user_id, name, id_card, relation)
SELECT 2000 + (u.id - 1000),
       u.id,
       CONCAT('压测患者', LPAD(u.id - 1000, 3, '0')),
       NULL,
       0
FROM tb_user u
WHERE u.id BETWEEN 1001 AND 1500;
