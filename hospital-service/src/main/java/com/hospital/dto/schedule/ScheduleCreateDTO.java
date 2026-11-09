package com.hospital.dto.schedule;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增排班（放号）入参。
 *
 * 差异点：不传 leftNum——剩余号数由服务端初始化为 totalNum，之后只能由挂号扣减；
 * 不传 departmentId——由医生所属科室带出，避免"医生在內科、排班却挂外科"的数据不一致。
 */
@Data
public class ScheduleCreateDTO {

    @NotNull(message = "医生不能为空")
    private Long doctorId;

    /** 出诊日期，不能是过去的日期 */
    @NotNull(message = "出诊日期不能为空")
    @FutureOrPresent(message = "出诊日期不能早于今天")
    private LocalDate workDate;

    /** 1上午 2下午 3晚间 */
    @NotNull(message = "时段不能为空")
    @Min(value = 1, message = "时段只能是 1上午 2下午 3晚间")
    @Max(value = 3, message = "时段只能是 1上午 2下午 3晚间")
    private Integer timeSlot;

    /** 放号总数 */
    @NotNull(message = "放号总数不能为空")
    @Min(value = 1, message = "放号总数至少为 1")
    private Integer totalNum;

    @NotNull(message = "挂号费不能为空")
    @DecimalMin(value = "0", message = "挂号费不能为负数")
    @DecimalMax(value = "99999999.99", message = "挂号费超出上限")
    private BigDecimal price;

    /** 1可预约 0停诊，默认可预约 */
    @Min(value = 0, message = "状态只能是 0停诊 1可预约")
    @Max(value = 1, message = "状态只能是 0停诊 1可预约")
    private Integer status;
}
