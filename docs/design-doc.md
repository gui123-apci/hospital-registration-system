# 智慧医院就医服务系统 — 设计文档

> 面向患者就医服务链路的轻量级系统。核心：挂号秒杀（防超卖）+ AI 导诊 Agent。
> 技术栈：Spring Boot（单体）/ MySQL / Redis / LangChain4j（或 Spring AI）

---

## 1. 项目定位与实习达标线

### 定位
不做大而全的 HIS（药房、电子病历、收费员一律砍掉），聚焦"患者服务链路 + 两条技术深线"。

### 达标线（做到这 3 样即达到/超过实习标准）
1. 业务闭环完整：挂号 → 支付 → 签到 → 叫号 → 就诊 → 退号/查报告，无断头。
2. 一条技术深线：秒杀防超卖链路能讲透（预扣库存、Lua、分布式锁、一人一单、异步下单、延迟释放、幂等）。
3. 一个差异化亮点：AI 导诊 Agent 打通「问答 → 查号 → 引导挂号」。

---

## 2. 系统架构

```
前端（两个工程）
  ├── patient-web  用户端（患者）
  └── admin-web    管理端（管理员 + 医生两个角色）

后端（一个 Spring Boot 单体）
  └── 分层：controller / service / mapper
      认证鉴权：JWT + Spring Security + 角色枚举

中间件（收敛为 2 个）
  ├── MySQL   持久化
  └── Redis   缓存 + 锁 + Stream 异步 + ZSet 延迟 + 向量检索（一挑五）
```

### 角色权限
| 角色 | 标识 | 权限范围 |
|------|------|---------|
| 患者 | PATIENT | C 端登录、挂号、查单、查报告、AI 问答 |
| 管理员 | ADMIN | 科室/医生/排班/订单管理、数据看板 |
| 医生 | DOCTOR | 我的排班、叫号面板 |

---

## 3. 核心状态机

### 号源状态机（tb_number_source.status）
```
0 可用 ──下单预扣──▶ 1 锁定 ──支付成功──▶ 2 已售出 ──就诊完成──▶ 3 已就诊
  ▲                   │                    │
  │  支付超时/取消     │   退号             │
  └────回补号池────────┘  └────回补号池──────┘
```

### 订单状态机（tb_order.status）
```
0 待支付 ──支付成功──▶ 1 已支付 ──医生叫号──▶ 6 就诊中 ──完成接诊──▶ 3 已就诊
   │                    │                                        ▲
   │                    ├──退号──────▶ 2 已退号                    │
   │                    │                                         │
   └─超时未付──▶ 4 已取消  └──未签到(夜间收尾)──▶ 5 已爽约             │
                                                              夜间收尾(就诊中)──┘
```

### 就医完整闭环（已实现）
```
浏览排班 → 挂号(秒杀) → 支付(超时自动释放) → [就诊当天] 签到 → 进候诊队列
→ 医生叫号 → 就诊中 → 完成接诊 → 生成报告 → 患者查报告
```

---

## 4. 数据库设计（9 张表）

```sql
-- ============ 1. 用户/账号 ============
CREATE TABLE tb_user (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL COMMENT '登录名（手机号）',
    password    VARCHAR(64)  NOT NULL COMMENT 'BCrypt 加密密码',
    phone       VARCHAR(11)  DEFAULT NULL COMMENT '手机号',
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
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科室表';

-- ============ 4. 医生 ============
CREATE TABLE tb_doctor (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    department_id BIGINT UNSIGNED NOT NULL COMMENT '所属科室',
    name          VARCHAR(32) NOT NULL COMMENT '医生姓名',
    title         VARCHAR(32) DEFAULT NULL COMMENT '职称',
    intro         VARCHAR(255) DEFAULT NULL COMMENT '简介',
    create_time   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_department_id (department_id)
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
    PRIMARY KEY (id),
    KEY idx_doctor_date (doctor_id, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排班表';

-- ============ 6. 号源（具体到"每一个号"）============
CREATE TABLE tb_number_source (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    schedule_id BIGINT UNSIGNED NOT NULL COMMENT '所属排班',
    source_no   INT          NOT NULL COMMENT '号序号',
    status      TINYINT      DEFAULT 0 COMMENT '0可用 1锁定 2已售出 3已就诊',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
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
    pay_time      DATETIME     DEFAULT NULL COMMENT '支付时间',
    deadline_time DATETIME     DEFAULT NULL COMMENT '支付截止时间（超时释放）',
    sign_time     DATETIME     DEFAULT NULL COMMENT '到院签到时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_patient_schedule (patient_id, schedule_id)
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
    PRIMARY KEY (id),
    KEY idx_patient_id (patient_id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检验报告表';
```

---

## 5. Redis 数据结构设计

### 5.1 已实现

| 场景 | Key | 结构 | TTL | 说明 |
|------|-----|------|-----|------|
| 号源库存 | `stock:{scheduleId}` | String | 1h | 懒加载回填 + Lua 原子扣减，防超卖 |
| 登录态/JWT | 无（JWT 自包含） | - | 2h | 服务端不存会话，token 解析即鉴权 |

### 5.2 规划方案（未实现）

| 场景 | Key | 结构 | TTL | 说明 |
|------|-----|------|-----|------|
| 一人一单锁 | `lock:order:{patientId}:{scheduleId}` | String | 短(秒级) | 以就诊人为粒度 |
| 排班缓存 | `cache:schedule:{scheduleId}` | Hash | 30min | 缓存穿透/击穿/雪崩场景 |
| 防重复下单幂等 | `idempotent:order:{userId}:{scheduleId}` | String | 短 | 幂等 |
| 未支付延迟释放 | `delay:order:pay` | ZSet | 无 | score=支付截止时间戳 |
| 异步下单队列 | `stream:order` | Stream | 持久 | 消费者组削峰 |
| 叫号队列 | `queue:call:{scheduleId}` | ZSet | 当日 | score=号序，签到入队 |
| 验证码 | `code:{phone}` | String | 5min | 登录 |
| AI 会话记忆 | `chat:history:{userId}` | List | 2h | 多轮对话 |
| 向量检索(RAG) | `rag:doc:index` | Redis Search | 持久 | 就医知识库向量索引 |

---

## 6. API 接口清单（已实现）

### 认证与账号
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /auth/register | 注册（仅建账号，不自动建就诊人）|
| POST | /auth/login | 登录（JWT）|
| GET  | /auth/me | 当前用户信息 |

### 患者端
| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | /department/list | 科室列表 |
| GET  | /doctor/list | 医生列表 |
| GET  | /schedule/list | 排班列表（含剩余号数）|
| GET  | /patient/list | 我的就诊人列表 |
| POST | /patient | 新增就诊人 |
| PUT  | /patient | 编辑就诊人 |
| DELETE | /patient/{id} | 删除就诊人 |
| POST | /order | 挂号下单（Redis Lua + 数据库条件 UPDATE 双保险）|
| GET  | /order/list | 我的挂号单 |
| GET  | /order/{id} | 订单详情 |
| POST | /order/sign | 到院签到 |
| POST | /payment/pay | 模拟支付 |
| POST | /payment/refund | 退号（号源回补）|
| GET  | /report/list | 报告查询 |
| POST | /triage/ask | AI 导诊（Function Calling，非流式）|

### 医生端
| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | /doctor-work/schedules | 我的出诊排班 |
| GET  | /doctor-work/waiting | 候诊队列（已签到未就诊）|
| POST | /doctor-work/call | 叫号（订单 → 就诊中）|
| POST | /doctor-work/complete | 完成接诊（就诊中 → 已就诊）|
| GET  | /doctor-work/current | 当前接诊患者（刷新恢复）|
| POST | /doctor-work/report | 开具检验报告（就诊中/已就诊）|

### 管理端
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /admin/department | 新增科室 |
| PUT  | /admin/department | 编辑科室 |
| DELETE | /admin/department/{id} | 删除科室 |
| GET  | /admin/department/page | 科室分页 |
| POST | /admin/doctor | 新增医生（自动开户）|
| PUT  | /admin/doctor | 编辑医生 |
| DELETE | /admin/doctor/{id} | 删除医生 |
| GET  | /admin/doctor/page | 医生分页 |
| POST | /admin/schedule | 放号 |
| PUT  | /admin/schedule | 编辑排班 |
| DELETE | /admin/schedule/{id} | 停诊 |
| GET  | /admin/schedule/page | 排班分页 |

---

## 7. 秒杀核心链路（防超卖）

### 7.1 当前实现

```
1. 管理端放号 → 号源写入数据库（tb_number_source）
2. 患者下单：
   a. 就诊人归属校验（只能给自己名下的就诊人挂号）
   b. 排班可预约校验（存在、未停诊、时段未过期）
   c. 一人一单预查（uk_patient_schedule 唯一键兜底）
   d. Redis 懒加载回填 + Lua 原子扣减库存（stock:{scheduleId}）
   e. 数据库条件 UPDATE 兜底（left_num > 0 才扣，防超卖）
3. 生成订单（状态=待支付），锁定号源
4. 支付成功 → 订单状态=已支付，号源=已售出
5. 定时任务扫描待支付订单 → 超时未支付 → 订单=已取消，库存回补
```

**关键面试点**：Lua 原子性、数据库唯一键兜底、库存预扣+延迟回补、条件 UPDATE 防并发。

### 7.2 规划方案（未实现）

```
1. 幂等校验（idempotent key）
2. 一人一单分布式锁（lock:order:{patientId}:{scheduleId}）
3. 订单写入 Stream 异步处理（消费者组削峰）
4. 写入 ZSet 延迟释放（score=支付截止时间戳）
```

---

## 8. AI 导诊 Agent 设计（分层）

### 8.1 当前实现

- **L2 Function Calling**：问"明天呼吸科还有号吗" → 调号源查询接口 → 返回实时数据；"帮我挂号" → 引导跳转
- **L3 工程**：医疗合规护栏（拒答诊断类问题，只做流程答疑）、结构化号源返回（前端可跳转预约）

### 8.2 规划方案（未实现）

- **L1 RAG 问答**：就医流程/科室介绍/医保政策 → 文档切片 → Embedding → Redis 向量检索 → LLM 生成
- **L3 工程**：SSE 流式输出、会话记忆（Redis）

---

## 9. 开发里程碑

| 阶段 | 内容 | 产出 | 状态 |
|------|------|------|------|
| M1 | 建表 + 基础 CRUD + 登录鉴权 | 可运行骨架 | ✅ 已完成 |
| M2 | 挂号秒杀链路（预扣/Lua/锁/释放） | 核心深线 | ✅ 已完成（异步/幂等为规划方案） |
| M3 | 签到 + 叫号 + 就诊中 + 完成接诊 + 退号 + 报告查询 | 闭环完整 | ✅ 已完成 |
| M4 | AI 导诊 Agent（Function Calling） | 差异化亮点 | ✅ 已完成（RAG/SSE 为规划方案） |
| M5 | 压测(k6) + Docker 部署 + 文档 | 交付物 | ✅ 已完成 |
| M6 | 医生账号自动开户 + 注册流程优化 | 数据一致性 | ✅ 已完成 |
| M7 | 就诊中状态闭环 + 夜间收尾覆盖 + 服务端恢复 | 状态机健壮性 | ✅ 已完成 |

### 规划方案（未排期）

- 异步下单（Stream 消费者组）
- 下单幂等（idempotent key）
- RAG 知识库问答（向量检索）
- SSE 流式输出
- 数据看板（/admin/dashboard）