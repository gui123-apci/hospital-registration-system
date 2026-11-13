package com.hospital.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 退号入参。只退已支付的单：退号 = 退款 + 号源回可用 + 库存回补。
 */
@Data
public class RefundDTO {

    @NotNull(message = "订单不能为空")
    @Positive(message = "订单参数不合法")
    private Long orderId;
}
