package com.hospital.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 Redis 的轻量分布式锁，专供定时任务使用。
 *
 * 存在意义：@Scheduled 是单机的，服务部署两个实例后，两个实例会同时扫同一批超时订单。
 * 不会错账（释放/收尾都是条件 UPDATE 幂等），但会白跑一遍、互相抢行锁。
 *
 * 设计取舍：
 * 1. 不等待重试——定时任务错过本轮，下轮还会来，没必要自旋。
 * 2. 释放校验 token——用 Lua 比对后删除，避免 A 的锁 TTL 到期后误删 B 刚拿到的锁。
 * 3. Redis 不可用时降级直跑——宁可两个实例重复跑（幂等无害），也不能一个都不跑（订单永不释放）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

    /** 只有锁的持有者才能删除；比对和删除在 Lua 里原子完成 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 拿到锁才执行，拿不到直接跳过本轮。
     *
     * @return true 表示执行了任务（含 Redis 故障降级执行），false 表示锁被其他实例持有
     */
    public boolean runIfAcquired(String key, Duration ttl, Runnable task) {
        String token;
        try {
            token = tryLock(key, ttl);
            if (token == null) {
                log.debug("锁被其他实例持有，跳过本轮任务: {}", key);
                return false;
            }
        } catch (Exception e) {
            // Redis 挂了：降级为单机执行。重复执行无副作用，漏执行才有问题
            log.warn("Redis 不可用，分布式锁降级为直接执行: {}", key, e);
            task.run();
            return true;
        }
        try {
            task.run();
            return true;
        } finally {
            unlock(key, token);
        }
    }

    /** 尝试加锁，不等待；成功返回持有凭证，被占用返回 null */
    private String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    private void unlock(String key, String token) {
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
        } catch (Exception e) {
            // 释放失败不影响正确性：TTL 到期后锁自动失效
            log.warn("释放分布式锁失败，等待 TTL 自动过期: {}", key, e);
        }
    }
}
