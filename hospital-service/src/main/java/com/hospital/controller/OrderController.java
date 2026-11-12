package com.hospital.controller;

import com.hospital.common.constant.OrderConstants;
import com.hospital.common.result.PageResult;
import com.hospital.common.result.Result;
import com.hospital.dto.order.OrderCreateDTO;
import com.hospital.security.LoginUser;
import com.hospital.service.OrderService;
import com.hospital.vo.order.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者端挂号接口（需登录）。
 * userId 从 token 解出，不由前端传。
 */
@Tag(name = "挂号订单", description = "患者预约挂号、查看我的订单")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "挂号下单", description = "秒杀式抢号：Redis 扣库存 + 数据库兜底")
    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateDTO dto,
                                  @AuthenticationPrincipal LoginUser loginUser) {
        // 超时分钟数与 Service 共用同一常量，改配置不用满项目找文案
        return Result.success("挂号成功，请在 " + OrderConstants.PAY_TIMEOUT_MINUTES + " 分钟内支付",
                orderService.create(dto, loginUser.getUserId()));
    }

    @Operation(summary = "我的订单（分页）")
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> listMine(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(orderService.pageMine(pageNum, pageSize, loginUser.getUserId()));
    }
}
