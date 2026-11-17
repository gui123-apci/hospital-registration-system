package com.hospital.vo.doctor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 候诊队列条目：医生工作台（或候诊大屏）展示"还有谁在等"。
 * 只读展示用，不参与状态流转；叫号仍由 callNext 一次取一条。
 */
@Data
public class WaitingVO {

    /** 排队序号，从 1 开始，按签到时间先后 */
    private Integer seq;

    private Long orderId;

    private String orderNo;

    private String patientName;

    private LocalDateTime signTime;
}
