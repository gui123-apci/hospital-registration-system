package com.hospital.common.enums;

import lombok.Getter;

/**
 * 支付渠道：0模拟 1微信 2支付宝。
 * 当前只有模拟支付（channel 只是记录流水用），真实渠道对接是 M5 之后的事。
 */
@Getter
public enum PaymentChannel {

    MOCK(0, "模拟"),
    WECHAT(1, "微信"),
    ALIPAY(2, "支付宝");

    private final int code;
    private final String desc;

    PaymentChannel(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
