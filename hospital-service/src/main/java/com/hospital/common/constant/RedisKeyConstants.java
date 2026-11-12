package com.hospital.common.constant;

/**
 * Redis key 统一管理：前缀只在这里定义一份，所有类引用同一处，
 * 避免 "stock:" 散落在多个类里各自拼写（单一事实来源）。
 */
public final class RedisKeyConstants {

    /** 排班库存 key 前缀，拼排班 id：stock:{scheduleId} */
    public static final String STOCK_PREFIX = "stock:";

    private RedisKeyConstants() {
    }
}
