package com.hospital.common.enums;

import lombok.Getter;

/**
 * 排班状态：0停诊 1可预约。
 */
@Getter
public enum ScheduleStatus {

    STOPPED(0, "停诊"),
    AVAILABLE(1, "可预约");

    private final int code;
    private final String desc;

    ScheduleStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
