package com.hospital;

import com.hospital.util.DistributedLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分布式锁行为验证：多实例部署时定时任务不能重复执行。
 */
@SpringBootTest
class DistributedLockTest {

    @Autowired
    private DistributedLock distributedLock;

    @Test
    void onlyOneInstanceRunsTaskAtATimeAndLockIsReusable() throws Exception {
        String key = "lock:test:" + UUID.randomUUID();
        Duration ttl = Duration.ofSeconds(10);
        AtomicInteger executed = new AtomicInteger();
        CountDownLatch holderStarted = new CountDownLatch(1);
        CountDownLatch holderCanFinish = new CountDownLatch(1);

        // 模拟实例 A：拿到锁后卡住不放手
        Thread instanceA = new Thread(() -> distributedLock.runIfAcquired(key, ttl, () -> {
            executed.incrementAndGet();
            holderStarted.countDown();
            try {
                holderCanFinish.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        instanceA.start();
        assertThat(holderStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // 模拟实例 B：同一时刻抢锁失败，直接跳过本轮，不该执行任务
        boolean instanceBRan = distributedLock.runIfAcquired(key, ttl, executed::incrementAndGet);
        assertThat(instanceBRan).isFalse();
        assertThat(executed.get()).isEqualTo(1);

        // A 执行完释放锁后，下一轮还能正常获取（锁不是一次性的）
        holderCanFinish.countDown();
        instanceA.join(5000);
        boolean nextRoundRan = distributedLock.runIfAcquired(key, ttl, executed::incrementAndGet);
        assertThat(nextRoundRan).isTrue();
        assertThat(executed.get()).isEqualTo(2);
    }
}
