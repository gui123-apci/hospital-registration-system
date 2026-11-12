package com.hospital.common.enums;

import lombok.Getter;

/**
 * 订单状态：0待支付 1已支付 2已退号 3已就诊 4已取消 5爽约。
 * 状态机：待支付 →(支付)已支付 →(就诊)已就诊；待支付 →(超时/取消)已取消；已支付 →(退号)已退号。
 */
@Getter
public enum OrderStatus {

    WAIT_PAY(0, "待支付"),
    PAID(1, "已支付"),
    REFUNDED(2, "已退号"),
    FINISHED(3, "已就诊"),
    CANCELED(4, "已取消"),
    NO_SHOW(5, "爽约");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
