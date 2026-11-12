package com.hospital.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 挂号下单入参。
 *
 * 差异点：不传 userId——账号身份从 token 里解出，前端传了就等于告诉服务器"我是谁"，
 * 改一下参数就能替别人下单。所以只有排班 + 就诊人两个业务字段。
 */
@Data
public class OrderCreateDTO {

    @NotNull(message = "排班不能为空")
    @Positive(message = "排班参数不合法")
    private Long scheduleId;

    @NotNull(message = "就诊人不能为空")
    @Positive(message = "就诊人参数不合法")
    private Long patientId;
}
