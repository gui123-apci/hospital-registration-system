package com.hospital.util;

import com.hospital.common.constant.RedisKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 库存缓存清理工具：所有"数据库库存变了，让 Redis 缓存作废"的地方共用同一份。
 * （原来这段逻辑在 OrderServiceImpl / PaymentServiceImpl / ScheduleServiceImpl 各抄了一份。）
 *
 * 为什么要在事务提交后（afterCommit）才删 key：
 * 在事务内直接删的坑——删完 key 数据库还没提交，并发请求 setIfAbsent 回填的是
 * 未提交的旧 left_num；等本事务提交后，Redis 比数据库少 1。不会超卖（数据库兜底），
 * 但会少卖，且偏差要等 TTL 1 小时过期才自愈，退号高峰期持续累积。
 * afterCommit 保证删 key 发生在数据库新值对所有人可见之后。
 */
@Slf4j
public final class StockCacheUtil {

    private StockCacheUtil() {
    }

    /**
     * 注册"事务提交后删除库存缓存"的回调。必须在事务内调用。
     * 删除失败只记日志：删不掉的后果是短暂读到旧库存，数据库兜底不会让账出错。
     */
    public static void evictAfterCommit(StringRedisTemplate stringRedisTemplate, Long scheduleId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doDelete(stringRedisTemplate, scheduleId);
                }
            });
        } else {
            // 没有事务上下文（调用点都在 @Transactional 里，理论走不到）：退化为直接删
            doDelete(stringRedisTemplate, scheduleId);
        }
    }

    private static void doDelete(StringRedisTemplate stringRedisTemplate, Long scheduleId) {
        try {
            stringRedisTemplate.delete(RedisKeyConstants.STOCK_PREFIX + scheduleId);
        } catch (DataAccessException e) {
            log.warn("删除 Redis 库存缓存失败, scheduleId={}", scheduleId, e);
        }
    }
}
