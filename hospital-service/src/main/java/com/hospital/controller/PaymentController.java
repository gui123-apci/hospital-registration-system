package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.dto.payment.PayDTO;
import com.hospital.dto.payment.RefundDTO;
import com.hospital.security.LoginUser;
import com.hospital.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者端支付/退号接口（需登录）。
 * userId 从 token 解出，不由前端传。
 */
@Tag(name = "支付", description = "挂号支付、退号退款")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "支付")
    @PostMapping("/pay")
    public Result<Void> pay(@Valid @RequestBody PayDTO dto,
                            @AuthenticationPrincipal LoginUser loginUser) {
        paymentService.pay(dto, loginUser.getUserId());
        return Result.success("支付成功", null);
    }

    @Operation(summary = "退号退款")
    @PostMapping("/refund")
    public Result<Void> refund(@Valid @RequestBody RefundDTO dto,
                               @AuthenticationPrincipal LoginUser loginUser) {
        paymentService.refund(dto, loginUser.getUserId());
        return Result.success("退号成功，已退款", null);
    }
}
