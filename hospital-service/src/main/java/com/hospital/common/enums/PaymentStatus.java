package com.hospital.common.enums;

import lombok.Getter;

/**
 * 支付单状态：0待支付 1成功 2失败 3退款。
 */
@Getter
public enum PaymentStatus {

    WAIT_PAY(0, "待支付"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败"),
    REFUNDED(3, "退款");

    private final int code;
    private final String desc;

    PaymentStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
