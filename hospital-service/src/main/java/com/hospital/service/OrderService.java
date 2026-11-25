package com.hospital.service;

import com.hospital.common.result.PageResult;
import com.hospital.dto.order.OrderCreateDTO;
import com.hospital.vo.order.OrderVO;

import java.util.List;

/**
 * 挂号订单业务接口。
 */
public interface OrderService {

    /**
     * 挂号下单：Redis Lua 扣库存 → 数据库事务兜底落库。
     *
     * @param dto    排班 + 就诊人
     * @param userId 从 token 解出的账号，服务端自己填，不收前端传
     */
    OrderVO create(OrderCreateDTO dto, Long userId);

    /**
     * 我的订单：分页查当前账号名下的挂号单，倒序。
     *
     * @param status 订单状态筛选，null 表示不过滤
     */
    PageResult<OrderVO> pageMine(int pageNum, int pageSize, Integer status, Long userId);

    /**
     * 查询已超时未支付的订单 id 列表（LIMIT 分批），供定时任务扫描。
     */
    List<Long> listExpiredWaitPayOrderIds(int limit);

    /**
     * 释放单个超时订单（独立事务，供定时任务逐条调用）：
     * 订单置已取消 + 号源回可用 + 库存回补 + 唯一键让位。
     *
     * ⚠ 内部方法：不做归属校验，仅限定时任务调用，不得暴露给 Controller。
     */
    void releaseExpiredOrder(Long orderId);

    /**
     * 到院签到：就诊日当天、时段结束前，给已支付订单写 sign_time。
     * 订单状态保持"已支付"不动，"已就诊/爽约"由收尾任务按 sign_time 有无分流。
     */
    void sign(Long orderId, Long userId);

    /**
     * 查询需收尾的订单 id（status=已支付 且 排班时段已结束，LIMIT 分批），供收尾任务扫描。
     */
    List<Long> listToFinishOrderIds(int limit);

    /**
     * 收尾单个订单（独立事务，供收尾任务逐条调用）：
     * 已支付且时段已结束的订单，sign_time 有值 → 已就诊，无值 → 爽约。
     * 号源、库存、active_flag 都不动：时段已过，号已消耗，无重挂意义。
     *
     * ⚠ 内部方法：不做归属校验，仅限定时任务调用，不得暴露给 Controller。
     */
    void finishOrder(Long orderId);
}
