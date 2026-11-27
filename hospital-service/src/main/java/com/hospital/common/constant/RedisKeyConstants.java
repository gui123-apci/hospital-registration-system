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

    private RedisKeyConstants() {
    }
}
