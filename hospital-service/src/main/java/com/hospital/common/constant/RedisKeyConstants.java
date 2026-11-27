package com.hospital.common.constant;

import java.time.Duration;

/**
 * Redis key 统一管理：前缀只在这里定义一份，所有类引用同一处，
 * 避免 "stock:" 散落在多个类里各自拼写（单一事实来源）。
 */
public final class RedisKeyConstants {

    /** 排班库存 key 前缀，拼排班 id：stock:{scheduleId} */
    public static final String STOCK_PREFIX = "stock:";

    /**
     * 库存 key 的存活时间：到期自动消失，下次挂号懒加载从数据库回填真值。
     * 作用是"自愈"——任何库存偏差（回补失败、回包丢失、脏写）最多存活这 1 小时；
     * 过期排班的 key 也靠它清理，不会永久残留。
     */
    public static final Duration STOCK_KEY_TTL = Duration.ofHours(1);

    /** AI 导诊限流 key 前缀，拼 userId：triage:lock:{userId} */
    public static final String TRIAGE_LOCK_PREFIX = "triage:lock:";

    /** 导诊限流窗口：同一用户 5 秒内只允许一次，防连点/刷新重复调模型 */
    public static final Duration TRIAGE_LOCK_TTL = Duration.ofSeconds(5);

    /** AI 会话记忆 key 前缀，拼 userId：triage:history:{userId} */
    public static final String TRIAGE_HISTORY_PREFIX = "triage:history:";

    /** 会话记忆存活时间：2 小时无操作自动清除 */
    public static final Duration TRIAGE_HISTORY_TTL = Duration.ofHours(2);

    /** 会话记忆最大轮数：只保留最近 10 轮（20 条消息），防止上下文过长 */
    public static final int TRIAGE_HISTORY_MAX_ROUNDS = 10;

    /** 下单幂等 key 前缀，拼 userId + requestId：idempotent:order:{userId}:{requestId} */
    public static final String IDEMPOTENT_PREFIX = "idempotent:order:";

    /** 幂等窗口 5 分钟：覆盖用户网络超时后重试的场景，过期后 requestId 自动失效 */
    public static final Duration IDEMPOTENT_TTL = Duration.ofMinutes(5);

    /** 幂等占位值：请求已受理但订单还没落库 */
    public static final String IDEMPOTENT_PENDING = "PENDING";

    /** 定时任务分布式锁前缀，拼任务名：lock:task:{name} */
    public static final String TASK_LOCK_PREFIX = "lock:task:";

    /** 超时释放任务锁 TTL：任务本身几秒跑完并主动释放，留 5 分钟兜底防宕机锁死 */
    public static final Duration TIMEOUT_TASK_LOCK_TTL = Duration.ofMinutes(5);

    /** 就诊收尾任务锁 TTL：一天一次，留 30 分钟兜底 */
    public static final Duration FINISH_TASK_LOCK_TTL = Duration.ofMinutes(30);

    private RedisKeyConstants() {
    }
}
