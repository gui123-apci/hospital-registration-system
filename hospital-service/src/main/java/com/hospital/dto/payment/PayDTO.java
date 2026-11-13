package com.hospital.dto.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 支付入参。
 * 差异点：不传金额——金额以订单里的 amount 为准，前端传了就会被改金额，服务端不信任。
 * channel 可选，默认 0 模拟支付。
 */
@Data
public class PayDTO {

    @NotNull(message = "订单不能为空")
    @Positive(message = "订单参数不合法")
    private Long orderId;

    /** 0模拟 1微信 2支付宝 */
    @Min(value = 0, message = "支付渠道不合法")
    @Max(value = 2, message = "支付渠道不合法")
    private Integer channel;
}
