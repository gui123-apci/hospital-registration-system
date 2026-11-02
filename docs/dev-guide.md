# 开发规范

> 适用于 `hospital-service` 后端工程，目的是让代码可读、可维护、可讲清楚。

## 一、工程结构

```
com.hospital
├── HospitalServiceApplication.java   启动类
├── config/          配置类（Redis、Security、MyBatis-Plus、WebMvc、Knife4j）
├── controller/      控制层，只做参数校验与调用 service
├── service/         业务接口
│   └── impl/        业务实现
├── mapper/          MyBatis-Plus Mapper
├── entity/          数据库实体（与表一一对应）
├── dto/             入参对象（请求体）
├── vo/              出参对象（响应视图）
├── common/
│   ├── result/      统一返回 Result
│   ├── exception/   业务异常 + 全局异常处理
│   └── constant/    常量、枚举
└── util/            工具类
```

**原则**：entity 不直接暴露给前端，controller 一律用 dto 入、vo 出。

## 二、命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 包名 | 全小写 | `com.hospital.service` |
| 类名 | 大驼峰 | `ScheduleService` |
| 方法 / 变量 | 小驼峰 | `createOrder`、`scheduleId` |
| 常量 | 全大写下划线 | `MAX_PAY_MINUTES` |
| 枚举 | 大驼峰 + 全大写值 | `OrderStatus.WAIT_PAY` |
| 数据库表 | `tb_` 前缀 + 下划线 | `tb_number_source` |
| Redis Key | 冒号分层 | `stock:schedule:1001` |

**避免的命名**：`data`、`info`、`list1`、`temp`、拼音缩写。

## 三、分层职责

| 层 | 职责 | 禁止 |
|----|------|------|
| Controller | 接收参数、校验、调 service、返回 Result | 写业务逻辑、直接调 mapper |
| Service | 业务编排、事务、缓存、锁 | 处理 HTTP 相关对象 |
| Mapper | 数据访问 | 写业务判断 |

- 跨表写操作必须加 `@Transactional`
- 事务内不要做远程调用 / 长时间操作
- Redis 操作统一封装在 service 层，不散落在 controller

## 四、统一返回与异常

**统一返回体**

```java
public class Result<T> {
    private Integer code;    // 200 成功，其余为业务错误码
    private String msg;
    private T data;
}
```

**业务异常**

- 统一抛 `BusinessException(code, msg)`，由 `@RestControllerAdvice` 全局捕获
- 禁止在 controller 里 `try-catch` 后拼装错误信息
- 错误码分段：1xxx 参数、2xxx 业务、3xxx 权限、5xxx 系统

## 五、Redis Key 规范

格式：`业务:对象:标识`，全部小写，冒号分隔。

| 用途 | Key 模板 | 结构 | TTL |
|------|----------|------|-----|
| 号源库存 | `stock:schedule:{scheduleId}` | String | 无 |
| 一人一单锁 | `lock:order:{patientId}:{scheduleId}` | String | 短 |
| 排班缓存 | `cache:schedule:{scheduleId}` | Hash | 30min |
| 未支付延迟 | `delay:order:pay` | ZSet | 无 |
| 异步下单 | `stream:order` | Stream | 持久 |
| 幂等标记 | `idem:order:{userId}:{scheduleId}` | String | 短 |
| 排队叫号 | `queue:call:{scheduleId}` | ZSet | 当日 |
| 会话记忆 | `chat:session:{sessionId}` | List/String | 30min |

**要求**：所有 key 集中定义在 `RedisKeyConstant`，禁止字符串硬编码散落各处。

## 六、Git 提交规范

提交信息格式：`type(scope): 描述`

| type | 含义 |
|------|------|
| feat | 新功能 |
| fix | 修复 bug |
| refactor | 重构 |
| perf | 性能优化 |
| docs | 文档 |
| test | 测试 |
| chore | 构建 / 依赖 / 配置 |

示例：

```
feat(order): 实现挂号秒杀预扣库存与 Lua 原子扣减
fix(order): 修复退号后号源未回补的问题
perf(schedule): 排班详情增加本地缓存，P99 降低 40%
```

**要求**：一个提交只做一件事；不提交 `target/`、`.idea/`、密钥文件。

## 七、注释规范

- 类、公共方法写 Javadoc，说明「做什么、为什么」
- 复杂业务逻辑写行内注释，解释**决策原因**而不是复述代码
- 秒杀、锁、延迟队列等关键代码必须注释清楚时序
- 不写无意义注释（如 `// 设置 id`）

## 八、安全与配置规范

- 密码使用 BCrypt 加密，禁止明文入库
- JWT 密钥放环境变量 / 配置中心，禁止硬编码进 Git
- 数据库密码、LLM API Key 放本地配置，不提交仓库
- 所有管理端接口做角色校验（ADMIN / DOCTOR）
- 患者只能访问自己的订单、就诊人、报告（越权校验）
- 日志禁止打印密码、身份证号、Token
