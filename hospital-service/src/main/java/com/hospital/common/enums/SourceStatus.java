package com.hospital.common.enums;

import lombok.Getter;

/**
 * 号源状态：0可用 1锁定 2已售出 3已就诊。
 * 状态机：可用 →(下单)锁定 →(支付成功)已售出 →(就诊)已就诊；
 * 锁定 →(超时未支付/取消)回可用；已售出 →(退号)回可用。
 * "锁定"就是"占着还没付钱"，和订单的待支付一一对应。
 */
@Getter
public enum SourceStatus {

    AVAILABLE(0, "可用"),
    LOCKED(1, "锁定"),
    SOLD(2, "已售出"),
    VISITED(3, "已就诊");

    private final int code;
    private final String desc;

    SourceStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
