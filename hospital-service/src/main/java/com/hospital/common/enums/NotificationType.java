package com.hospital.common.enums;

import lombok.Getter;

/**
 * 站内通知类型：1支付成功 2医生叫号 3报告出具。
 */
@Getter
public enum NotificationType {

    PAY_SUCCESS(1, "支付成功"),
    CALL_NUMBER(2, "医生叫号"),
    REPORT_READY(3, "报告出具");

    private final int code;
    private final String desc;

    NotificationType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
