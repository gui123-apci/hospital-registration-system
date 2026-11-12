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

    private RedisKeyConstants() {
    }
}
