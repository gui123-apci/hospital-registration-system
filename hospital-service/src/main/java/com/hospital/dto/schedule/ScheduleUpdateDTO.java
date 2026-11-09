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
 * 更新排班入参：id 必传，其余字段为 null 表示不修改。
 * 医生不可改——换医生等于换一条排班，应删了重建。
 */
@Data
public class ScheduleUpdateDTO {

    @NotNull(message = "排班ID不能为空")
    private Long id;

    @FutureOrPresent(message = "出诊日期不能早于今天")
    private LocalDate workDate;

    @Min(value = 1, message = "时段只能是 1上午 2下午 3晚间")
    @Max(value = 3, message = "时段只能是 1上午 2下午 3晚间")
    private Integer timeSlot;

    /** 调整放号总数，剩余号数会按已挂出的数量同步换算 */
    @Min(value = 1, message = "放号总数至少为 1")
    private Integer totalNum;

    @DecimalMin(value = "0", message = "挂号费不能为负数")
    @DecimalMax(value = "99999999.99", message = "挂号费超出上限")
    private BigDecimal price;

    /** 1可预约 0停诊 */
    @Min(value = 0, message = "状态只能是 0停诊 1可预约")
    @Max(value = 1, message = "状态只能是 0停诊 1可预约")
    private Integer status;
}
