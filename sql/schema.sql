-- ============================================================
-- 智慧医院就医服务系统 - 建库建表脚本
-- MySQL 8.x / JDK 17 / Spring Boot 3.x
-- ============================================================

DROP DATABASE IF EXISTS hospital;
CREATE DATABASE hospital DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE hospital;

-- ============ 1. 用户/账号 ============
CREATE TABLE tb_user (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL COMMENT '登录名（手机号）',
    password    VARCHAR(64)  NOT NULL COMMENT 'BCrypt 加密密码',
    phone       VARCHAR(11)  DEFAULT NULL COMMENT '手机号',
    role        TINYINT      NOT NULL DEFAULT 0 COMMENT '角色：0患者 1管理员 2医生',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============ 2. 就诊人（实名制，一个账号可挂多个家人）============
CREATE TABLE tb_patient (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '所属账号',
    name        VARCHAR(32)  NOT NULL COMMENT '就诊人姓名',
    id_card     VARCHAR(18)  NOT NULL COMMENT '身份证号',
    relation    TINYINT      DEFAULT 0 COMMENT '关系：0本人 1父母 2子女 3其他',
    phone       VARCHAR(11)  DEFAULT NULL COMMENT '联系电话',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_id_card (id_card),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='就诊人表';

-- ============ 3. 科室 ============
CREATE TABLE tb_department (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32) NOT NULL COMMENT '科室名',
    description VARCHAR(255) DEFAULT NULL COMMENT '科室简介',
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科室表';

-- ============ 4. 医生 ============
CREATE TABLE tb_doctor (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    department_id BIGINT UNSIGNED NOT NULL COMMENT '所属科室',
    name          VARCHAR(32) NOT NULL COMMENT '医生姓名',
    title         VARCHAR(32) DEFAULT NULL COMMENT '职称',
    intro         VARCHAR(255) DEFAULT NULL COMMENT '简介',
    user_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '关联登录账号（医生角色），可为空',
    create_time   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_department_id (department_id),
    UNIQUE KEY uk_doctor_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生表';

-- ============ 5. 排班（号源库存的"池"）============
CREATE TABLE tb_schedule (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    doctor_id     BIGINT UNSIGNED NOT NULL COMMENT '医生',
    department_id BIGINT UNSIGNED NOT NULL COMMENT '科室',
    work_date     DATE         NOT NULL COMMENT '出诊日期',
    time_slot     TINYINT      NOT NULL COMMENT '时段：1上午 2下午 3晚间',
    total_num     INT          NOT NULL COMMENT '放号总数',
    left_num      INT          NOT NULL COMMENT '剩余号数（秒杀库存字段）',
    price         DECIMAL(10,2) DEFAULT 0 COMMENT '挂号费',
    status        TINYINT      DEFAULT 1 COMMENT '1可预约 0停诊',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doctor_date_slot (doctor_id, work_date, time_slot),
    KEY idx_doctor_date (doctor_id, work_date),
    KEY idx_work_date (work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排班表';

-- ============ 6. 号源（具体到"每一个号"）============
CREATE TABLE tb_number_source (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    schedule_id BIGINT UNSIGNED NOT NULL COMMENT '所属排班',
    source_no   INT          NOT NULL COMMENT '号序号',
    status      TINYINT      DEFAULT 0 COMMENT '0可用 1锁定 2已售出 3已就诊',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_source (schedule_id, source_no),
    KEY idx_schedule_status (schedule_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='号源表';

-- ============ 7. 挂号单（主订单）============
CREATE TABLE tb_order (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no      VARCHAR(32) NOT NULL COMMENT '订单号（幂等/防重复）',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '账号',
    patient_id    BIGINT UNSIGNED NOT NULL COMMENT '就诊人',
    schedule_id   BIGINT UNSIGNED NOT NULL COMMENT '排班',
    source_id     BIGINT UNSIGNED NOT NULL COMMENT '号源',
    amount        DECIMAL(10,2) DEFAULT 0 COMMENT '金额',
    status        TINYINT      DEFAULT 0 COMMENT '0待支付 1已支付 2已退号 3已就诊 4已取消 5爽约',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    pay_time      DATETIME     DEFAULT NULL COMMENT '支付时间',
    deadline_time DATETIME     DEFAULT NULL COMMENT '支付截止时间（超时释放）',
    sign_time     DATETIME     DEFAULT NULL COMMENT '到院签到时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_patient_schedule (patient_id, schedule_id),
    KEY idx_schedule_id (schedule_id),
    KEY idx_status_deadline (status, deadline_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='挂号单表';

-- ============ 8. 支付单 ============
CREATE TABLE tb_payment (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT UNSIGNED NOT NULL COMMENT '关联订单',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '账号',
    patient_id  BIGINT UNSIGNED NOT NULL COMMENT '就诊人',
    amount      DECIMAL(10,2) DEFAULT 0 COMMENT '金额',
    channel     TINYINT      DEFAULT 0 COMMENT '支付渠道：0模拟 1微信 2支付宝',
    status      TINYINT      DEFAULT 0 COMMENT '0待支付 1成功 2失败 3退款',
    pay_time    DATETIME     DEFAULT NULL COMMENT '支付时间',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单表';

-- ============ 9. 检验报告 ============
CREATE TABLE tb_report (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    patient_id  BIGINT UNSIGNED NOT NULL COMMENT '就诊人',
    order_id    BIGINT UNSIGNED NOT NULL COMMENT '关联挂号单',
    doctor_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '出报告医生',
    report_type VARCHAR(32)  DEFAULT NULL COMMENT '报告类型',
    title       VARCHAR(64)  DEFAULT NULL COMMENT '报告标题',
    content     TEXT COMMENT '报告内容',
    report_time DATETIME     DEFAULT NULL COMMENT '报告时间',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_patient_id (patient_id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验报告表';
