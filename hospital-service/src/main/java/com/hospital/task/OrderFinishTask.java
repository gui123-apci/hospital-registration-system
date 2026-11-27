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
 * 就诊状态收尾定时任务：把当天时段已结束、仍停在"已支付"的订单推到终态。
 *
 * 每天 22:00 触发——晚间时段 21:00 结束，此刻今天所有时段都已结束，
 * 所以"时段结束了吗"这个问题不用在 SQL 里回答，cron 表达式本身就是答案。
 *
 * 分流依据只有一个字段 sign_time：有值（签到了但没叫完）→ 已就诊，无值 → 爽约。
 * 号源、库存、active_flag 都不动：时段已过，号已消耗，无重挂意义。
 *
 * 多实例部署：Redis 分布式锁保证只有一台执行。重复收尾本身幂等，锁只是避免白跑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFinishTask {

    /** 分布式锁 key */
    private static final String LOCK_KEY = RedisKeyConstants.TASK_LOCK_PREFIX + "order-finish";

    /** 每批处理条数 */
    private static final int BATCH_SIZE = 500;

    /** 单轮最多批数 */
    private static final int MAX_BATCHES = 4;

    private final OrderService orderService;
    private final DistributedLock distributedLock;

    @Scheduled(cron = "0 0 22 * * ?")
    public void finishTodayOrders() {
        distributedLock.runIfAcquired(LOCK_KEY, RedisKeyConstants.FINISH_TASK_LOCK_TTL,
                this::doFinishTodayOrders);
    }

    private void doFinishTodayOrders() {
        int finished = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Long> ids = orderService.listToFinishOrderIds(BATCH_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            // 逐条独立事务，和超时释放同一套思路：一条脏数据失败不连累其他
            for (Long id : ids) {
                try {
                    orderService.finishOrder(id);
                    finished++;
                } catch (Exception e) {
                    log.error("订单就诊状态收尾失败, orderId={}", id, e);
                }
            }
        }
        if (finished > 0) {
            log.info("本轮收尾订单 {} 条", finished);
        }
    }
}
