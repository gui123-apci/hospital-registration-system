package com.hospital.common.constant;

/**
 * AI 导诊业务常量：结果来源标记，前端据此区分是模型、缓存还是规则命中。
 */
public final class TriageConstants {

    /** 模型实时推荐 */
    public static final String SOURCE_AI = "AI";

    /** 命中缓存（同一症状 24 小时内） */
    public static final String SOURCE_CACHE = "CACHE";

    /** 关键词规则兜底（模型超时/异常/科室对不上） */
    public static final String SOURCE_RULE = "RULE";

    private TriageConstants() {
    }
}
