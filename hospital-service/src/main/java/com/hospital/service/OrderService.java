package com.hospital.service;

import com.hospital.common.result.PageResult;
import com.hospital.dto.order.OrderCreateDTO;
import com.hospital.vo.order.OrderVO;

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
     */
    PageResult<OrderVO> pageMine(int pageNum, int pageSize, Long userId);
}
