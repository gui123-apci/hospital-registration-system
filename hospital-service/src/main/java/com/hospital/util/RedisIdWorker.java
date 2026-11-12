package com.hospital.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一 ID 生成器：时间戳(高 32 位) 拼 当日自增序列(低 32 位)。
 *
 * 为什么不用数据库自增 id 当订单号：
 * 1. 泄露业务量（订单号能看出平台总共多少单）；
 * 2. 可枚举（把号改成下一个值就能遍历试探别人的订单）；
 * 3. 分库分表后多个库各自自增会撞号。
 *
 * 时间戳保证"不同秒不重复"，Redis INCR 保证"同一秒内也不重复"。
 */
@Component
@RequiredArgsConstructor
public class RedisIdWorker {

    /** 起始时间戳（2024-01-01 00:00:00 UTC），让生成的 id 位数更短 */
    private static final long BEGIN_TIMESTAMP = 1704067200L;

    private final StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 按天分 key，每天从 1 重新计数，不用设置过期，靠日期字符串天然分隔
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        long seq = count == null ? 1L : count;

        return (timestamp << 32) | seq;
    }
}
