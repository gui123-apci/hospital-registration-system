package com.hospital.common.enums;

import lombok.Getter;

/**
 * 订单状态：0待支付 1已支付 2已退号 3已就诊 4已取消 5爽约 6就诊中。
 * 状态机：待支付 →(支付)已支付 →(叫号)就诊中 →(完成接诊)已就诊；
 * 待支付 →(超时/取消)已取消；已支付 →(退号)已退号；已支付 →(超时未签到)爽约。
 */
@Getter
public enum OrderStatus {

    WAIT_PAY(0, "待支付"),
    PAID(1, "已支付"),
    REFUNDED(2, "已退号"),
    FINISHED(3, "已就诊"),
    CANCELED(4, "已取消"),
    NO_SHOW(5, "爽约"),
    IN_PROGRESS(6, "就诊中");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 按 code 反查枚举，脏数据（不认识的 code）返回 null，由调用方决定兜底 */
    public static OrderStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
