package com.hospital.task;

import com.hospital.common.constant.RedisKeyConstants;
import com.hospital.service.OrderService;
import com.hospital.util.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单超时释放定时任务。
 *
 * 分批消费：每批 500 条、单轮最多 4 批（2000 条），剩下的下一分钟接着扫——
 * 服务停机重启或放号量大积压几千上万条时，不会一轮跑几十分钟把号一直占着。
 *
 * 多实例部署：用 Redis 分布式锁保证同一时刻只有一个实例扫单。
 * 即使锁失效导致重复执行也不会错账——释放逻辑是条件 UPDATE，天然幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    /** 分布式锁 key：所有实例抢同一把锁 */
    private static final String LOCK_KEY = RedisKeyConstants.TASK_LOCK_PREFIX + "order-timeout";

    /** 每批处理条数 */
    private static final int BATCH_SIZE = 500;

    /** 单轮最多批数（防一轮跑太久，剩余下轮继续） */
    private static final int MAX_BATCHES = 4;

    private final OrderService orderService;
    private final DistributedLock distributedLock;

    /**
     * 每 60 秒扫一次；fixedDelay 保证上一次跑完再计时，不会堆积；
     * initialDelay 让连接池/缓存先热身 30 秒再干第一票活
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void releaseExpiredOrders() {
        distributedLock.runIfAcquired(LOCK_KEY, RedisKeyConstants.TIMEOUT_TASK_LOCK_TTL,
                this::doReleaseExpiredOrders);
    }

    private void doReleaseExpiredOrders() {
        int released = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Long> ids = orderService.listExpiredWaitPayOrderIds(BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            // 逐条独立事务：一条脏数据失败只记日志，不影响其他订单，
            // 也避免把所有超时单塞进一个长事务里锁住一堆行
            for (Long id : ids) {
                try {
                    orderService.releaseExpiredOrder(id);
                    released++;
                } catch (Exception e) {
                    log.error("超时订单释放失败, orderId={}", id, e);
                }
            }
        }
        if (released > 0) {
            log.info("本轮释放超时未支付订单 {} 条", released);
        }
    }
}
