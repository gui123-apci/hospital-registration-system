package com.hospital.vo.triage;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 导诊推荐的可预约号源（结构化），供前端渲染"一键挂号"入口。
 *
 * 三个 id 缺一不可：前端确认挂号页靠 scheduleId + doctorId + departmentId 三键
 * 校验医生、排班、科室的对应关系，防止拼错链接或篡改链接参数越权。
 */
@Data
public class TriageScheduleVO {

    private Long scheduleId;

    private Long doctorId;

    private Long departmentId;

    private String doctorName;

    private LocalDate workDate;

    /** 1上午 2下午 3晚间 */
    private Integer timeSlot;

    private Integer leftNum;

    private BigDecimal price;
}
